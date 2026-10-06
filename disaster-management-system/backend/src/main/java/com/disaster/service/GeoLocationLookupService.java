package com.disaster.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * High-accuracy server-side lookup service for Indian states and city centroids.
 * Used during user/organisation registration and geolocation migrations.
 */
@Service
public class GeoLocationLookupService {

    private static final Logger log = LoggerFactory.getLogger(GeoLocationLookupService.class);

    public record Coordinates(double latitude, double longitude, boolean approximate) {}

    public static final Coordinates INDIA_CENTRE = new Coordinates(20.5937, 78.9629, true);

    private final Map<String, Coordinates> cityCoordinates;
    private final Map<String, Coordinates> stateCoordinates;

    public GeoLocationLookupService() {
        Map<String, Coordinates> cities = new HashMap<>();
        Map<String, Coordinates> states = new HashMap<>();

        // â”€â”€ 1. States & Union Territories Centroids â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
        states.put("andhra pradesh", new Coordinates(15.9129, 79.7400, true));
        states.put("arunachal pradesh", new Coordinates(28.2180, 94.7278, true));
        states.put("assam", new Coordinates(26.2006, 92.9376, true));
        states.put("bihar", new Coordinates(25.0961, 85.3131, true));
        states.put("chhattisgarh", new Coordinates(21.2787, 81.8661, true));
        states.put("goa", new Coordinates(15.2993, 74.1240, true));
        states.put("gujarat", new Coordinates(22.2587, 71.1924, true));
        states.put("haryana", new Coordinates(29.0588, 76.0856, true));
        states.put("himachal pradesh", new Coordinates(31.1048, 77.1734, true));
        states.put("jharkhand", new Coordinates(23.6102, 85.2799, true));
        states.put("karnataka", new Coordinates(15.3173, 75.7139, true));
        states.put("kerala", new Coordinates(10.8505, 76.2711, true));
        states.put("madhya pradesh", new Coordinates(22.9734, 78.6569, true));
        states.put("maharashtra", new Coordinates(19.7515, 75.7139, true));
        states.put("manipur", new Coordinates(24.6637, 93.9063, true));
        states.put("meghalaya", new Coordinates(25.4670, 91.3662, true));
        states.put("mizoram", new Coordinates(23.1645, 92.9376, true));
        states.put("nagaland", new Coordinates(26.1584, 94.5624, true));
        states.put("odisha", new Coordinates(20.9517, 85.0985, true));
        states.put("punjab", new Coordinates(31.1471, 75.3412, true));
        states.put("rajasthan", new Coordinates(27.0238, 74.2179, true));
        states.put("sikkim", new Coordinates(27.5330, 88.5122, true));
        states.put("tamil nadu", new Coordinates(11.1271, 78.6569, true));
        states.put("telangana", new Coordinates(18.1124, 79.0193, true));
        states.put("tripura", new Coordinates(23.9408, 91.9882, true));
        states.put("uttar pradesh", new Coordinates(26.8467, 80.9462, true));
        states.put("uttarakhand", new Coordinates(30.0668, 79.0193, true));
        states.put("west bengal", new Coordinates(22.9868, 87.8550, true));
        states.put("andaman and nicobar islands", new Coordinates(11.7401, 92.6586, true));
        states.put("chandigarh", new Coordinates(30.7333, 76.7794, true));
        states.put("dadra and nagar haveli and daman and diu", new Coordinates(20.1809, 73.0169, true));
        states.put("delhi (nct)", new Coordinates(28.6139, 77.2090, true));
        states.put("delhi", new Coordinates(28.6139, 77.2090, true));
        states.put("jammu and kashmir", new Coordinates(33.7782, 76.5762, true));
        states.put("ladakh", new Coordinates(34.1526, 77.5771, true));
        states.put("lakshadweep", new Coordinates(10.5667, 72.6417, true));
        states.put("puducherry", new Coordinates(11.9416, 79.8083, true));

        // â”€â”€ 2. Verified City Centroids â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
        // Andhra Pradesh
        cities.put("visakhapatnam", new Coordinates(17.6868, 83.2185, false));
        cities.put("vijayawada", new Coordinates(16.5062, 80.6480, false));
        cities.put("guntur", new Coordinates(16.3067, 80.4365, false));
        cities.put("nellore", new Coordinates(14.4426, 79.9865, false));
        cities.put("kurnool", new Coordinates(15.8281, 78.0373, false));
        cities.put("tirupati", new Coordinates(13.6288, 79.4192, false));
        cities.put("kakinada", new Coordinates(16.9891, 82.2475, false));
        cities.put("rajamahendravaram", new Coordinates(17.0005, 81.8040, false));
        cities.put("anantapur", new Coordinates(14.6819, 77.6006, false));
        cities.put("kadapa", new Coordinates(14.4673, 78.8242, false));

        // Arunachal Pradesh
        cities.put("itanagar", new Coordinates(27.0844, 93.6053, false));
        cities.put("naharlagun", new Coordinates(27.1042, 93.6966, false));
        cities.put("pasighat", new Coordinates(28.0664, 95.3268, false));
        cities.put("tawang", new Coordinates(27.5861, 91.8594, false));
        cities.put("ziro", new Coordinates(27.5950, 93.8385, false));
        cities.put("bomdila", new Coordinates(27.2645, 92.4229, false));

        // Assam
        cities.put("guwahati", new Coordinates(26.1445, 91.7362, false));
        cities.put("silchar", new Coordinates(24.8333, 92.7789, false));
        cities.put("dibrugarh", new Coordinates(27.4728, 94.9120, false));
        cities.put("jorhat", new Coordinates(26.7509, 94.2037, false));
        cities.put("nagaon", new Coordinates(26.3464, 92.6840, false));
        cities.put("tinsukia", new Coordinates(27.4922, 95.3468, false));
        cities.put("tezpur", new Coordinates(26.6528, 92.7926, false));

        // Bihar
        cities.put("patna", new Coordinates(25.5941, 85.1376, false));
        cities.put("gaya", new Coordinates(24.7914, 85.0002, false));
        cities.put("bhagalpur", new Coordinates(25.2425, 86.9842, false));
        cities.put("muzaffarpur", new Coordinates(26.1209, 85.3647, false));
        cities.put("purnia", new Coordinates(25.7771, 87.4753, false));
        cities.put("darbhanga", new Coordinates(26.1542, 85.8918, false));
        cities.put("bihar sharif", new Coordinates(25.1982, 85.5149, false));

        // Chhattisgarh
        cities.put("raipur", new Coordinates(21.2514, 81.6296, false));
        cities.put("bhilai", new Coordinates(21.2121, 81.3733, false));
        cities.put("bilaspur", new Coordinates(22.0797, 82.1409, false));
        cities.put("korba", new Coordinates(22.3595, 82.7501, false));
        cities.put("rajnandgaon", new Coordinates(21.0974, 81.0335, false));
        cities.put("durg", new Coordinates(21.1904, 81.2849, false));

        // Goa
        cities.put("panaji", new Coordinates(15.4909, 73.8278, false));
        cities.put("margao", new Coordinates(15.2832, 73.9862, false));
        cities.put("vasco da gama", new Coordinates(15.3982, 73.8113, false));
        cities.put("mapusa", new Coordinates(15.5937, 73.8142, false));
        cities.put("ponda", new Coordinates(15.4026, 74.0152, false));

        // Gujarat
        cities.put("ahmedabad", new Coordinates(23.0225, 72.5714, false));
        cities.put("surat", new Coordinates(21.1702, 72.8311, false));
        cities.put("vadodara", new Coordinates(22.3072, 73.1812, false));
        cities.put("rajkot", new Coordinates(22.3039, 70.8022, false));
        cities.put("bhavnagar", new Coordinates(21.7645, 72.1519, false));
        cities.put("jamnagar", new Coordinates(22.4707, 70.0577, false));
        cities.put("gandhinagar", new Coordinates(23.2156, 72.6369, false));
        cities.put("anand", new Coordinates(22.5645, 72.9289, false));

        // Haryana
        cities.put("gurugram", new Coordinates(28.4595, 77.0266, false));
        cities.put("faridabad", new Coordinates(28.4089, 77.3178, false));
        cities.put("panipat", new Coordinates(29.3909, 76.9635, false));
        cities.put("ambala", new Coordinates(30.3782, 76.7767, false));
        cities.put("rohtak", new Coordinates(28.8955, 76.6066, false));
        cities.put("hisar", new Coordinates(29.1492, 75.7217, false));
        cities.put("karnal", new Coordinates(29.6857, 76.9905, false));
        cities.put("panchkula", new Coordinates(30.6942, 76.8606, false));
        cities.put("sonipat", new Coordinates(28.9931, 77.0151, false));

        // Himachal Pradesh
        cities.put("shimla", new Coordinates(31.1048, 77.1734, false));
        cities.put("dharamshala", new Coordinates(32.2190, 76.3234, false));
        cities.put("solan", new Coordinates(30.9045, 77.0967, false));
        cities.put("mandi", new Coordinates(31.7087, 76.9320, false));
        cities.put("kullu", new Coordinates(31.9579, 77.1095, false));
        cities.put("manali", new Coordinates(32.2432, 77.1892, false));

        // Jharkhand
        cities.put("ranchi", new Coordinates(23.3441, 85.3096, false));
        cities.put("jamshedpur", new Coordinates(22.8046, 86.2029, false));
        cities.put("dhanbad", new Coordinates(23.7957, 86.4304, false));
        cities.put("bokaro", new Coordinates(23.6693, 86.1511, false));
        cities.put("deoghar", new Coordinates(24.4826, 86.6970, false));
        cities.put("hazaribagh", new Coordinates(23.9961, 85.3647, false));

        // Karnataka
        cities.put("bengaluru", new Coordinates(12.9716, 77.5946, false));
        cities.put("mysuru", new Coordinates(12.2958, 76.6394, false));
        cities.put("mangaluru", new Coordinates(12.9141, 74.8560, false));
        cities.put("hubballi-dharwad", new Coordinates(15.3647, 75.1240, false));
        cities.put("belagavi", new Coordinates(15.8497, 74.4977, false));
        cities.put("kalaburagi", new Coordinates(17.3297, 76.8343, false));
        cities.put("davanagere", new Coordinates(14.4644, 75.9218, false));
        cities.put("ballari", new Coordinates(15.1394, 76.9214, false));
        cities.put("shivamogga", new Coordinates(13.9299, 75.5681, false));
        cities.put("udupi", new Coordinates(13.3409, 74.7421, false));

        // Kerala
        cities.put("thiruvananthapuram", new Coordinates(8.5241, 76.9366, false));
        cities.put("kochi", new Coordinates(9.9312, 76.2673, false));
        cities.put("kozhikode", new Coordinates(11.2588, 75.7804, false));
        cities.put("thrissur", new Coordinates(10.5276, 76.2144, false));
        cities.put("kollam", new Coordinates(8.8932, 76.6141, false));
        cities.put("alappuzha", new Coordinates(9.4981, 76.3388, false));
        cities.put("palakkad", new Coordinates(10.7867, 76.6548, false));
        cities.put("kottayam", new Coordinates(9.5916, 76.5222, false));
        cities.put("wayanad", new Coordinates(11.6854, 76.1320, false));

        // Madhya Pradesh
        cities.put("bhopal", new Coordinates(23.2599, 77.4126, false));
        cities.put("indore", new Coordinates(22.7196, 75.8577, false));
        cities.put("jabalpur", new Coordinates(23.1815, 79.9864, false));
        cities.put("gwalior", new Coordinates(26.2183, 78.1828, false));
        cities.put("ujjain", new Coordinates(23.1765, 75.7885, false));
        cities.put("sagar", new Coordinates(23.8388, 78.7378, false));
        cities.put("dewas", new Coordinates(22.9676, 76.0534, false));

        // Maharashtra
        cities.put("mumbai", new Coordinates(19.0760, 72.8777, false));
        cities.put("pune", new Coordinates(18.5204, 73.8567, false));
        cities.put("nagpur", new Coordinates(21.1458, 79.0882, false));
        cities.put("thane", new Coordinates(19.2183, 72.9781, false));
        cities.put("nashik", new Coordinates(19.9975, 73.7898, false));
        cities.put("navi mumbai", new Coordinates(19.0330, 73.0297, false));
        cities.put("aurangabad (chhatrapati sambhaji nagar)", new Coordinates(19.8762, 75.3433, false));
        cities.put("aurangabad", new Coordinates(19.8762, 75.3433, false));
        cities.put("chhatrapati sambhaji nagar", new Coordinates(19.8762, 75.3433, false));
        cities.put("solapur", new Coordinates(17.6599, 75.9064, false));
        cities.put("kolhapur", new Coordinates(16.7050, 74.2433, false));

        // Manipur
        cities.put("imphal", new Coordinates(24.8170, 93.9368, false));
        cities.put("churachandpur", new Coordinates(24.3333, 93.6833, false));
        cities.put("thoubal", new Coordinates(24.6386, 94.0150, false));
        cities.put("bishnupur", new Coordinates(24.6324, 93.7584, false));

        // Meghalaya
        cities.put("shillong", new Coordinates(25.5788, 91.8933, false));
        cities.put("tura", new Coordinates(25.5141, 90.2033, false));
        cities.put("jowai", new Coordinates(25.4497, 92.2035, false));
        cities.put("nongpoh", new Coordinates(25.9036, 91.8803, false));

        // Mizoram
        cities.put("aizawl", new Coordinates(23.7271, 92.7176, false));
        cities.put("lunglei", new Coordinates(22.8671, 92.7656, false));
        cities.put("champhai", new Coordinates(23.4751, 93.3283, false));
        cities.put("serchhip", new Coordinates(23.3417, 92.8500, false));
        cities.put("kolasib", new Coordinates(24.2253, 92.6780, false));

        // Nagaland
        cities.put("kohima", new Coordinates(25.6751, 94.1086, false));
        cities.put("dimapur", new Coordinates(25.9095, 93.7266, false));
        cities.put("mokokchung", new Coordinates(26.3256, 94.5204, false));
        cities.put("tuensang", new Coordinates(26.2750, 94.8300, false));

        // Odisha
        cities.put("bhubaneswar", new Coordinates(20.2961, 85.8245, false));
        cities.put("cuttack", new Coordinates(20.4625, 85.8828, false));
        cities.put("rourkela", new Coordinates(22.2604, 84.8536, false));
        cities.put("berhampur", new Coordinates(19.3149, 84.7941, false));
        cities.put("sambalpur", new Coordinates(21.4669, 83.9812, false));
        cities.put("puri", new Coordinates(19.8135, 85.8312, false));

        // Punjab
        cities.put("ludhiana", new Coordinates(30.9010, 75.8573, false));
        cities.put("amritsar", new Coordinates(31.6340, 74.8723, false));
        cities.put("jalandhar", new Coordinates(31.3260, 75.5762, false));
        cities.put("patiala", new Coordinates(30.3398, 76.3869, false));
        cities.put("bathinda", new Coordinates(30.2110, 74.9455, false));
        cities.put("mohali (sas nagar)", new Coordinates(30.7046, 76.7179, false));
        cities.put("mohali", new Coordinates(30.7046, 76.7179, false));
        cities.put("sas nagar", new Coordinates(30.7046, 76.7179, false));

        // Rajasthan
        cities.put("jaipur", new Coordinates(26.9124, 75.7873, false));
        cities.put("jodhpur", new Coordinates(26.2389, 73.0243, false));
        cities.put("kota", new Coordinates(25.2138, 75.8648, false));
        cities.put("bikaner", new Coordinates(28.0229, 73.3119, false));
        cities.put("ajmer", new Coordinates(26.4499, 74.6399, false));
        cities.put("udaipur", new Coordinates(24.5854, 73.7125, false));

        // Sikkim
        cities.put("gangtok", new Coordinates(27.3389, 88.6065, false));
        cities.put("namchi", new Coordinates(27.1667, 88.3500, false));
        cities.put("geyzing", new Coordinates(27.2889, 88.2539, false));
        cities.put("mangan", new Coordinates(27.5050, 88.5280, false));

        // Tamil Nadu
        cities.put("chennai", new Coordinates(13.0827, 80.2707, false));
        cities.put("coimbatore", new Coordinates(11.0168, 76.9558, false));
        cities.put("madurai", new Coordinates(9.9252, 78.1198, false));
        cities.put("tiruchirappalli", new Coordinates(10.7905, 78.7047, false));
        cities.put("salem", new Coordinates(11.6643, 78.1460, false));
        cities.put("tiruppur", new Coordinates(11.1085, 77.3411, false));
        cities.put("tirunelveli", new Coordinates(8.7139, 77.7567, false));

        // Telangana
        cities.put("hyderabad", new Coordinates(17.3850, 78.4867, false));
        cities.put("warangal", new Coordinates(17.9689, 79.5941, false));
        cities.put("nizamabad", new Coordinates(18.6725, 78.0941, false));
        cities.put("karimnagar", new Coordinates(18.4386, 79.1288, false));
        cities.put("khammam", new Coordinates(17.2473, 80.1514, false));
        cities.put("ramagundam", new Coordinates(18.7557, 79.4739, false));

        // Tripura
        cities.put("agartala", new Coordinates(23.8315, 91.2868, false));
        cities.put("dharmanagar", new Coordinates(24.3725, 92.1642, false));
        cities.put("udaipur", new Coordinates(23.5333, 91.4833, false));
        cities.put("kailashahar", new Coordinates(24.3317, 92.0078, false));

        // Uttar Pradesh
        cities.put("lucknow", new Coordinates(26.8467, 80.9462, false));
        cities.put("kanpur", new Coordinates(26.4499, 80.3319, false));
        cities.put("varanasi", new Coordinates(25.3176, 82.9739, false));
        cities.put("agra", new Coordinates(27.1767, 78.0081, false));
        cities.put("prayagraj (allahabad)", new Coordinates(25.4358, 81.8463, false));
        cities.put("prayagraj", new Coordinates(25.4358, 81.8463, false));
        cities.put("allahabad", new Coordinates(25.4358, 81.8463, false));
        cities.put("noida", new Coordinates(28.5355, 77.3910, false));
        cities.put("greater noida", new Coordinates(28.4744, 77.5040, false));
        cities.put("ghaziabad", new Coordinates(28.6692, 77.4538, false));
        cities.put("meerut", new Coordinates(28.9845, 77.7064, false));
        cities.put("gorakhpur", new Coordinates(26.7606, 83.3732, false));

        // Uttarakhand
        cities.put("dehradun", new Coordinates(30.3165, 78.0322, false));
        cities.put("haridwar", new Coordinates(29.9457, 78.1642, false));
        cities.put("rishikesh", new Coordinates(30.0869, 78.2676, false));
        cities.put("roorkee", new Coordinates(29.8543, 77.8880, false));
        cities.put("haldwani", new Coordinates(29.2183, 79.5130, false));
        cities.put("nainital", new Coordinates(29.3919, 79.4542, false));

        // West Bengal
        cities.put("kolkata", new Coordinates(22.5726, 88.3639, false));
        cities.put("howrah", new Coordinates(22.5958, 88.2636, false));
        cities.put("siliguri", new Coordinates(26.7271, 88.3953, false));
        cities.put("asansol", new Coordinates(23.6739, 86.9524, false));
        cities.put("durgapur", new Coordinates(23.5204, 87.3119, false));
        cities.put("darjeeling", new Coordinates(27.0410, 88.2663, false));

        // Union Territories
        cities.put("port blair", new Coordinates(11.6234, 92.7265, false));
        cities.put("diglipur", new Coordinates(13.2678, 92.9696, false));
        cities.put("car nicobar", new Coordinates(9.1667, 92.7833, false));
        cities.put("chandigarh", new Coordinates(30.7333, 76.7794, false));
        cities.put("daman", new Coordinates(20.3974, 72.8328, false));
        cities.put("diu", new Coordinates(20.7144, 70.9874, false));
        cities.put("silvassa", new Coordinates(20.2763, 73.0083, false));
        cities.put("central delhi", new Coordinates(28.6500, 77.2300, false));
        cities.put("south delhi", new Coordinates(28.5355, 77.2100, false));
        cities.put("south west delhi", new Coordinates(28.5921, 77.0460, false));
        cities.put("west delhi", new Coordinates(28.6665, 77.0707, false));
        cities.put("north delhi", new Coordinates(28.7180, 77.1680, false));
        cities.put("north west delhi", new Coordinates(28.7250, 77.0850, false));
        cities.put("east delhi", new Coordinates(28.6279, 77.2784, false));
        cities.put("new delhi", new Coordinates(28.6139, 77.2090, false));
        cities.put("srinagar", new Coordinates(34.0837, 74.7973, false));
        cities.put("jammu", new Coordinates(32.7266, 74.8570, false));
        cities.put("anantnag", new Coordinates(33.7311, 75.1522, false));
        cities.put("baramulla", new Coordinates(34.1980, 74.3636, false));
        cities.put("leh", new Coordinates(34.1526, 77.5771, false));
        cities.put("kargil", new Coordinates(34.5539, 76.1349, false));
        cities.put("kavaratti", new Coordinates(10.5667, 72.6417, false));
        cities.put("agatti", new Coordinates(10.8533, 72.1931, false));
        cities.put("minicoy", new Coordinates(8.2833, 73.0500, false));
        cities.put("puducherry", new Coordinates(11.9416, 79.8083, false));
        cities.put("karaikal", new Coordinates(10.9254, 79.8380, false));
        cities.put("mahe", new Coordinates(11.7002, 75.5340, false));

        this.cityCoordinates = Collections.unmodifiableMap(cities);
        this.stateCoordinates = Collections.unmodifiableMap(states);
    }

    /**
     * Resolves coordinates given a city and optional state name.
     * Strategy:
     * 1. Exact or partial city match -> city coordinates.
     * 2. If city not found or blank, match state -> state centroid (approximate).
     * 3. If neither found -> Optional.empty().
     */
    public Optional<Coordinates> lookup(String city, String state) {
        if (city != null && !city.isBlank()) {
            String normCity = normalize(city);
            Coordinates cityCoord = cityCoordinates.get(normCity);
            if (cityCoord != null) {
                return Optional.of(cityCoord);
            }
            // Check substrings / partial matches for composite names like "Mohali (SAS Nagar)"
            for (Map.Entry<String, Coordinates> entry : cityCoordinates.entrySet()) {
                if (normCity.contains(entry.getKey()) || entry.getKey().contains(normCity)) {
                    return Optional.of(entry.getValue());
                }
            }
        }

        if (state != null && !state.isBlank()) {
            String normState = normalize(state);
            Coordinates stateCoord = stateCoordinates.get(normState);
            if (stateCoord != null) {
                return Optional.of(stateCoord);
            }
            for (Map.Entry<String, Coordinates> entry : stateCoordinates.entrySet()) {
                if (normState.contains(entry.getKey()) || entry.getKey().contains(normState)) {
                    return Optional.of(entry.getValue());
                }
            }
        }

        return Optional.empty();
    }

    private String normalize(String input) {
        return input.toLowerCase()
                .replaceAll("\\s+", " ")
                .trim();
    }
}
