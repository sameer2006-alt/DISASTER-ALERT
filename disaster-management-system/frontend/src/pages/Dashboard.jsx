import { useEffect, useMemo, useState } from 'react'
import { useNavigate, Link } from 'react-router-dom'
import { eventsApi, shelterApi, simulateApi, sachetApi, userApi } from '../lib/api'
import { createStompClient, subscribeAlerts, subscribeShelters } from '../lib/websocket'
import UnifiedDisasterMap from '../components/map/UnifiedDisasterMap'
import { MapPin } from 'lucide-react'

const DEFAULT_CENTER = { lat: 19.076, lng: 72.8777 }

export default function Dashboard() {
  const navigate = useNavigate()
  const demoMode = new URLSearchParams(window.location.search).get('demo') === 'true'
  
  const [alerts, setAlerts] = useState([])
  const [shelters, setShelters] = useState([])
  const [events, setEvents] = useState([])
  const [localEvents, setLocalEvents] = useState([])
  const [localAlerts, setLocalAlerts] = useState([])
  const [sachetEvents, setSachetEvents] = useState([])
  // userLocation used for SOS/simulate only — NOT passed to map to avoid auto-zoom
  const [userLocation, setUserLocation] = useState(DEFAULT_CENTER)
  const [updatingLocation, setUpdatingLocation] = useState(false)
  const [locationMsg, setLocationMsg] = useState('')

  const handleUpdateLocation = () => {
    if (!navigator.geolocation) {
      setLocationMsg('Geolocation not supported')
      setTimeout(() => setLocationMsg(''), 4000)
      return
    }
    setUpdatingLocation(true)
    setLocationMsg('')
    navigator.geolocation.getCurrentPosition(
      async (pos) => {
        try {
          const lat = pos.coords.latitude
          const lng = pos.coords.longitude
          await userApi.updateLocation({ latitude: lat, longitude: lng })
          setUserLocation({ lat, lng })
          setLocationMsg(`Location updated (${lat.toFixed(2)}, ${lng.toFixed(2)})`)
        } catch (err) {
          setLocationMsg(err.response?.data?.message || 'Failed to update location')
        } finally {
          setUpdatingLocation(false)
          setTimeout(() => setLocationMsg(''), 4000)
        }
      },
      () => {
        setUpdatingLocation(false)
        setLocationMsg('Location access denied')
        setTimeout(() => setLocationMsg(''), 4000)
      },
      { timeout: 10000, enableHighAccuracy: true }
    )
  }
  
  const activeLocation = userLocation
  
  const visibleShelters = useMemo(() => shelters, [shelters])
  const visibleEvents = useMemo(() => [...localEvents, ...events], [events, localEvents])
  // sachetAlerts feeds the bottom ticker alongside WS alerts and local drills
  const [sachetAlerts, setSachetAlerts] = useState([])
  const visibleAlerts = useMemo(
    () => [...sachetAlerts, ...localAlerts, ...alerts],
    [alerts, localAlerts, sachetAlerts]
  )


  useEffect(() => {
    if (!localStorage.getItem('token')) {
      if (demoMode) {
        localStorage.setItem('token', 'demo-token')
        localStorage.setItem('role', 'ADMIN')
        localStorage.setItem('username', 'Mission Lead')
      } else {
        navigate('/login')
        return
      }
    }
    
    // GPS is fetched silently for SOS/simulate accuracy but does NOT move the map
    if (navigator.geolocation) {
      navigator.geolocation.getCurrentPosition(
        (pos) => setUserLocation({ lat: pos.coords.latitude, lng: pos.coords.longitude }),
        () => {} // silent fail — map stays at Pan India view
      )
    }

    loadData()
    loadSachetAlerts()

    const client = createStompClient((c) => {
      subscribeAlerts(c, (a) => {
        // If incoming WS alert is SACHET_NDMA, merge into map events AND ticker
        if (a.source === 'SACHET_NDMA') {
          const mapEvent = {
            id: a.id,
            disasterType: a.disasterType,
            title: a.message,
            message: a.message,
            location: a.location,
            latitude: a.latitude,
            longitude: a.longitude,
            severity: a.severity,
            affectedRadius: a.affectedRadius || 25,
            source: 'SACHET_NDMA',
            state: a.state,
            officialSeverity: a.officialSeverity,
            sourceUrl: a.sourceUrl,
            timestamp: a.timestamp,
          }
          setSachetEvents((prev) => {
            if (prev.some((e) => e.id === mapEvent.id)) return prev
            return [mapEvent, ...prev].slice(0, 100)
          })
          // Also push to bottom ticker
          setSachetAlerts((prev) => {
            if (prev.some((e) => e.id === a.id)) return prev
            return [{
              id: a.id,
              disasterType: a.disasterType,
              message: a.message,
              location: a.location,
              source: 'SACHET_NDMA',
              state: a.state,
              officialSeverity: a.officialSeverity,
              sourceUrl: a.sourceUrl,
            }, ...prev].slice(0, 50)
          })
        }
        setAlerts((prev) => [a, ...prev].slice(0, 20))
        loadData()

      })
      subscribeShelters(c, (s) => {
        setShelters((prev) => {
          const idx = prev.findIndex((x) => x.id === s.id)
          if (idx >= 0) {
            const copy = [...prev]
            copy[idx] = s
            return copy
          }
          return [...prev, s]
        })
      })
    })
    return () => client.deactivate()
  }, [demoMode, navigate])

  const loadData = () => {
    eventsApi.active().then((r) => setEvents(r.data)).catch(() => {})
    shelterApi.list().then((r) => setShelters(r.data)).catch(() => {})
  }

  const loadSachetAlerts = () => {
    sachetApi.alerts({ limit: 50 })
      .then((r) => {
        const data = r.data?.alerts || []

        // Map Python SACHET event format → DisasterEvent-like shape for map markers
        const mappedEvents = data.map((ev) => ({
          id: ev.id,
          disasterType: ev.disasterType,
          title: ev.message,
          message: ev.message,
          location: ev.location,
          latitude: ev.latitude,
          longitude: ev.longitude,
          severity: ev.severity,
          affectedRadius: ev.affectedRadius || 25,
          source: 'SACHET_NDMA',
          state: ev.state,
          officialSeverity: ev.officialSeverity,
          sourceUrl: ev.sourceUrl,
          timestamp: ev.timestamp,
        }))
        setSachetEvents(mappedEvents)

        // Also feed the bottom ticker — same shape but only needs ticker fields
        const tickerAlerts = data.map((ev) => ({
          id: ev.id,
          disasterType: ev.disasterType,
          message: ev.message,
          location: ev.location,
          source: 'SACHET_NDMA',
          state: ev.state,
          officialSeverity: ev.officialSeverity,
          sourceUrl: ev.sourceUrl,
        }))
        setSachetAlerts(tickerAlerts)
      })
      .catch(() => {})
  }


  const simulate = async (type) => {
    try {
      const fn = simulateApi[type.toLowerCase()] || simulateApi.disaster
      await fn({
        type,
        severity: 9,
        location: `Simulated ${type} — ${userLocation?.city || 'Live Region'}`,
        latitude: activeLocation.lat + (Math.random() * 0.05 - 0.025),
        longitude: activeLocation.lng + (Math.random() * 0.05 - 0.025),
        affectedRadius: 15,
      })
      loadData()
    } catch (err) {
      const drillEvent = {
        id: `local-${Date.now()}`,
        disasterType: type,
        message: `${type} drill activated near live user location`,
        location: 'Live GPS sector',
        latitude: activeLocation.lat,
        longitude: activeLocation.lng,
        severity: 9,
        affectedRadius: 15
      }
      setLocalEvents((prev) => [drillEvent, ...prev].slice(0, 8))
      setLocalAlerts((prev) => [
        {
          id: `local-alert-${Date.now()}`,
          disasterType: type,
          message: 'Local simulation overlay activated',
          location: drillEvent.location,
        },
        ...prev,
      ].slice(0, 8))
    }
  }

  return (
    <div style={{ width: '100vw', height: '100vh', overflow: 'hidden', position: 'relative' }}>
      {/* Floating Verification Pipeline Telemetry Badge */}
      <div className="absolute top-4 left-1/2 -translate-x-1/2 z-30 pointer-events-auto">
        <Link
          to="/verification"
          className="flex items-center gap-2 px-4 py-2 rounded-full glass border border-emerald-500/40 text-xs font-medium text-emerald-300 hover:text-white hover:border-emerald-400 bg-cinematic-black/80 backdrop-blur-md shadow-lg shadow-emerald-950/40 transition-all hover:scale-105"
        >
          <span className="w-2 h-2 rounded-full bg-emerald-400 animate-pulse" />
          <span className="font-semibold">Cross-Source Verification:</span>
          <span className="text-slate-200">Panic Shield Active</span>
          <span className="text-accent-blue font-bold ml-1 flex items-center">
            Open Pipeline &rarr;
          </span>
        </Link>
      </div>

      {/* Floating User Location Update Control */}
      <div className="absolute top-4 right-4 z-30 pointer-events-auto flex items-center gap-2">
        {locationMsg && (
          <span className="px-3 py-1.5 rounded-full text-xs font-medium bg-cinematic-black/90 border border-emerald-500/40 text-emerald-300 backdrop-blur-md shadow-lg animate-in fade-in">
            {locationMsg}
          </span>
        )}
        <button
          type="button"
          onClick={handleUpdateLocation}
          disabled={updatingLocation}
          className="flex items-center gap-1.5 px-3 py-2 rounded-full glass border border-white/15 text-xs font-medium text-slate-200 hover:text-white hover:border-accent-blue bg-cinematic-black/80 backdrop-blur-md shadow-lg transition-all hover:scale-105 disabled:opacity-50"
          title="Update your location on server for localized alerts"
        >
          <MapPin className="w-3.5 h-3.5 text-accent-blue shrink-0" />
          <span>{updatingLocation ? 'Updating...' : 'Update my location'}</span>
        </button>
      </div>

      {/* center prop intentionally omitted — map opens at Pan India zoom-5 view */}
      <UnifiedDisasterMap
        events={visibleEvents}
        shelters={visibleShelters}
        alerts={visibleAlerts}
        onSimulate={simulate}
      />
    </div>
  )
}
