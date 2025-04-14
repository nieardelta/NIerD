import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;

import java.sql.*;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Scanner;

public class GeoIPService {

    private static final String YANDEX_GEOCODER_URL = "https://geocode-maps.yandex.ru/1.x/";
    private static final String API_KEY = "8797dcd0-3405-4adc-ad3d-a615198e0371";
    private static final String DB_URL = "jdbc:postgresql://localhost:5432/demo";
    private static final String DB_USER = "postgres";
    private static final String DB_PASSWORD = "123";

    public static void main(String[] args) {
        Scanner scanner = new Scanner(System.in);

        System.out.println("Выберите тип запроса:");
        System.out.println("1 - По IP-адресу");
        System.out.println("2 - По координатам");
        int choice = scanner.nextInt();
        scanner.nextLine();

        if (choice == 1) {
            handleIpRequest(scanner);
        } else if (choice == 2) {
            handleCoordinateRequest(scanner);
        } else {
            System.out.println("Неверный выбор");
        }
    }

    private static void handleIpRequest(Scanner scanner) {
        System.out.print("Введите IP-адрес: ");
        String ipAddress = scanner.nextLine();

        String result = getDataFromDatabaseByIp(ipAddress);
        if (result != null) {
            System.out.println("Данные из БД: " + result);
            if (isDataOutdated(ipAddress, 0, 0, true)) { // Передаем 0 для координат, так как они не используются
                System.out.println("Данные устарели. Делаем запрос к Яндекс API...");
                String jsonResponse = getAddressByIp(ipAddress);
                if (jsonResponse != null) {
                    updateIpRequestAndResponse(ipAddress, jsonResponse);
                    result = getDataFromDatabaseByIp(ipAddress);
                    System.out.println("Данные из Яндекс API: " + result);
                } else {
                    System.out.println("Не удалось получить данные от Яндекс API.");
                }
            }
        } else {
            System.out.println("Данные не найдены в БД. Делаем запрос к Яндекс API...");
            String jsonResponse = getAddressByIp(ipAddress);
            if (jsonResponse != null) {
                saveIpRequestAndResponse(ipAddress, jsonResponse);
                result = getDataFromDatabaseByIp(ipAddress);
                System.out.println("Данные из Яндекс API: " + result);
            } else {
                System.out.println("Не удалось получить данные от Яндекс API.");
            }
        }
    }

    private static void handleCoordinateRequest(Scanner scanner) {
        System.out.print("Введите широту: ");
        double latitude = scanner.nextDouble();
        System.out.print("Введите долготу: ");
        double longitude = scanner.nextDouble();
        scanner.nextLine();

        String result = getDataFromDatabaseByCoordinates(latitude, longitude);
        if (result != null) {
            System.out.println("Данные из БД: " + result);
            if (isDataOutdated(null, latitude, longitude, false)) { // Передаем null для IP, так как запрос по координатам
                System.out.println("Данные устарели. Делаем запрос к Яндекс API...");
                String jsonResponse = getAddressByCoordinates(latitude, longitude);
                if (jsonResponse != null) {
                    updateCoordinateRequestAndResponse(latitude, longitude, jsonResponse);
                    result = getDataFromDatabaseByCoordinates(latitude, longitude);
                    System.out.println("Данные из Яндекс API: " + result);
                } else {
                    System.out.println("Не удалось получить данные от Яндекс API.");
                }
            }
        } else {
            System.out.println("Данные не найдены в БД. Делаем запрос к Яндекс API...");
            String jsonResponse = getAddressByCoordinates(latitude, longitude);
            if (jsonResponse != null) {
                saveCoordinateRequestAndResponse(latitude, longitude, jsonResponse);
                result = getDataFromDatabaseByCoordinates(latitude, longitude);
                System.out.println("Данные из Яндекс API: " + result);
            } else {
                System.out.println("Не удалось получить данные от Яндекс API.");
            }
        }
    }

    private static String getDataFromDatabaseByIp(String ipAddress) {
        String sql = "SELECT r.ip_address, res.country_name, res.city, ST_AsText(res.location) AS location, r.raw_response " +
                "FROM requests r " +
                "JOIN responses res ON r.id = res.request_id " +
                "WHERE r.ip_address = ?";

        try (Connection conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD);
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, ipAddress);
            ResultSet rs = stmt.executeQuery();

            if (rs.next()) {
                return String.format(
                        "IP: %s, Страна: %s, Город: %s, Координаты: %s, Raw: %s",
                        rs.getString("ip_address"),
                        rs.getString("country_name"),
                        rs.getString("city"),
                        rs.getString("location"),
                        rs.getString("raw_response")
                );
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return null;
    }

    private static String getDataFromDatabaseByCoordinates(double latitude, double longitude) {
        String sql = "SELECT ST_X(cr.location) AS latitude, ST_Y(cr.location) AS longitude, " +
                "res.country_name, res.city, ST_AsText(cr.location) AS location, cr.raw_response " +
                "FROM coordinate_requests cr " +
                "JOIN coordinate_responses res ON cr.id = res.request_id " +
                "WHERE ST_Distance(cr.location, ST_SetSRID(ST_MakePoint(?, ?), 4326)) < 0.0001";

        try (Connection conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD);
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setDouble(1, longitude);
            stmt.setDouble(2, latitude);
            ResultSet rs = stmt.executeQuery();

            if (rs.next()) {
                return String.format(
                        "Широта: %s, Долгота: %s, Страна: %s, Город: %s, Координаты: %s, Raw: %s",
                        rs.getDouble("latitude"),
                        rs.getDouble("longitude"),
                        rs.getString("country_name"),
                        rs.getString("city"),
                        rs.getString("location"),
                        rs.getString("raw_response")
                );
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return null;
    }

    private static String getAddressByIp(String ip) {
        try (CloseableHttpClient httpClient = HttpClients.createDefault()) {
            String url = String.format("%s?apikey=%s&geocode=%s&format=json", YANDEX_GEOCODER_URL, API_KEY, ip);
            return executeRequest(url);
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    private static String getAddressByCoordinates(double latitude, double longitude) {
        try (CloseableHttpClient httpClient = HttpClients.createDefault()) {
            String coordinates = String.format("%s,%s", longitude, latitude);
            String url = String.format("%s?apikey=%s&geocode=%s&format=json", YANDEX_GEOCODER_URL, API_KEY, coordinates);
            return executeRequest(url);
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    private static String executeRequest(String url) throws Exception {
        HttpGet request = new HttpGet(url);
        try (CloseableHttpResponse response = HttpClients.createDefault().execute(request)) {
            return EntityUtils.toString(response.getEntity());
        }
    }

    private static void saveIpRequestAndResponse(String ip, String jsonResponse) {
        Connection conn = null;
        try {
            conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD);
            conn.setAutoCommit(false);

            // Вставка в таблицу requests
            String insertRequestSQL = "INSERT INTO requests (ip_address, request_timestamp, raw_response) VALUES (?, ?, ?) RETURNING id";
            PreparedStatement requestStmt = conn.prepareStatement(insertRequestSQL);
            requestStmt.setString(1, ip);
            requestStmt.setTimestamp(2, Timestamp.valueOf(LocalDateTime.now()));
            requestStmt.setString(3, jsonResponse);
            ResultSet rs = requestStmt.executeQuery();
            rs.next();
            long requestId = rs.getLong(1);

            // Парсинг и сохранение ответа
            parseAndSaveResponse(conn, requestId, jsonResponse, false);

            conn.commit();
            System.out.println("Данные успешно сохранены в БД!");

        } catch (Exception e) {
            handleRollback(conn, e);
        } finally {
            closeConnection(conn);
        }
    }

    private static void saveCoordinateRequestAndResponse(double latitude, double longitude, String jsonResponse) {
        Connection conn = null;
        try {
            conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD);
            conn.setAutoCommit(false);

            // Вставка в таблицу coordinate_requests
            String insertRequestSQL = "INSERT INTO coordinate_requests (location, request_timestamp, raw_response) VALUES (ST_SetSRID(ST_MakePoint(?, ?), 4326), ?, ?) RETURNING id";
            PreparedStatement requestStmt = conn.prepareStatement(insertRequestSQL);
            requestStmt.setDouble(1, longitude);
            requestStmt.setDouble(2, latitude);
            requestStmt.setTimestamp(3, Timestamp.valueOf(LocalDateTime.now()));
            requestStmt.setString(4, jsonResponse);
            ResultSet rs = requestStmt.executeQuery();
            rs.next();
            long requestId = rs.getLong(1);

            // Парсинг и сохранение ответа
            parseAndSaveResponse(conn, requestId, jsonResponse, true);

            conn.commit();
            System.out.println("Данные успешно сохранены в БД!");

        } catch (Exception e) {
            handleRollback(conn, e);
        } finally {
            closeConnection(conn);
        }
    }

    private static void parseAndSaveResponse(Connection conn, long requestId, String jsonResponse, boolean isCoordinate) throws Exception {
        JsonNode root = new ObjectMapper().readTree(jsonResponse);
        JsonNode featureMember = root.path("response").path("GeoObjectCollection").path("featureMember").get(0);
        JsonNode geoObject = featureMember.path("GeoObject");
        JsonNode metaData = geoObject.path("metaDataProperty").path("GeocoderMetaData");

        String countryCode = metaData.path("Address").path("country_code").asText();
        String countryName = "";
        String city = "";
        JsonNode components = metaData.path("Address").path("Components");
        for (JsonNode component : components) {
            String kind = component.path("kind").asText();
            if (kind.equals("country")) {
                countryName = component.path("name").asText();
            } else if (kind.equals("locality")) {
                city = component.path("name").asText();
            }
        }

        String[] coordinates = geoObject.path("Point").path("pos").asText().split(" ");
        double longitude = Double.parseDouble(coordinates[0]);
        double latitude = Double.parseDouble(coordinates[1]);

        String insertSQL;
        if (isCoordinate) {
            insertSQL = "INSERT INTO coordinate_responses (request_id, country_code, country_name, city, location, response_timestamp, raw_response) VALUES (?, ?, ?, ?, ST_SetSRID(ST_MakePoint(?, ?), 4326), ?, ?)";
        } else {
            insertSQL = "INSERT INTO responses (request_id, country_code, country_name, city, location, response_timestamp, raw_response) VALUES (?, ?, ?, ?, ST_SetSRID(ST_MakePoint(?, ?), 4326), ?, ?)";
        }

        PreparedStatement responseStmt = conn.prepareStatement(insertSQL);
        responseStmt.setLong(1, requestId);
        responseStmt.setString(2, countryCode);
        responseStmt.setString(3, countryName);
        responseStmt.setString(4, city);
        responseStmt.setDouble(5, longitude);
        responseStmt.setDouble(6, latitude);
        responseStmt.setTimestamp(7, Timestamp.valueOf(LocalDateTime.now()));
        responseStmt.setString(8, jsonResponse);

        responseStmt.executeUpdate();
    }
        // Проверка по дате через колноку Time stamp
    private static boolean isDataOutdated(String ipAddress, double latitude, double longitude, boolean isIpRequest) {
        String sql;
        if (isIpRequest) {
            sql = "SELECT request_timestamp FROM requests WHERE ip_address = ? ORDER BY request_timestamp DESC LIMIT 1";
        } else {
            sql = "SELECT request_timestamp FROM coordinate_requests WHERE ST_Distance(location, ST_SetSRID(ST_MakePoint(?, ?), 4326)) < 0.0001 ORDER BY request_timestamp DESC LIMIT 1";
        }

        try (Connection conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD);
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            if (isIpRequest) {
                stmt.setString(1, ipAddress);
                System.out.println("Проверка свежести данных для IP: " + ipAddress);
            } else {
                stmt.setDouble(1, longitude);
                stmt.setDouble(2, latitude);
                System.out.println("Проверка свежести данных для координат: Широта=" + latitude + ", Долгота=" + longitude);
            }

            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                Timestamp timestamp = rs.getTimestamp("request_timestamp");
                LocalDateTime lastUpdate = timestamp.toLocalDateTime();
                LocalDateTime now = LocalDateTime.now();
                long daysSinceLastUpdate = ChronoUnit.DAYS.between(lastUpdate, now);

                // Логирование
                System.out.println("Последнее обновление: " + lastUpdate);
                System.out.println("Текущее время: " + now);
                System.out.println("Дней с последнего обновления: " + daysSinceLastUpdate);

                if (daysSinceLastUpdate > 30) {
                    System.out.println("Данные устарели (прошло более 30 дней).");
                    return true;
                } else {
                    System.out.println("Данные актуальны (прошло " + daysSinceLastUpdate + " дней).");
                    return false;
                }
            } else {
                System.out.println("Данные не найдены в базе. Считаем их устаревшими.");
                return true;
            }
        } catch (SQLException e) {
            System.err.println("Ошибка при выполнении SQL-запроса:");
            e.printStackTrace();
            return true; // В случае ошибки считаем данные устаревшими
        }
    }



    private static void updateIpRequestAndResponse(String ip, String jsonResponse) {
        Connection conn = null;
        try {
            conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD);
            conn.setAutoCommit(false);

            // Обновление данных в таблице requests
            String updateRequestSQL = "UPDATE requests SET request_timestamp = ?, raw_response = ? WHERE ip_address = ?";
            PreparedStatement requestStmt = conn.prepareStatement(updateRequestSQL);
            requestStmt.setTimestamp(1, Timestamp.valueOf(LocalDateTime.now()));
            requestStmt.setString(2, jsonResponse);
            requestStmt.setString(3, ip);
            requestStmt.executeUpdate();

            // Парсинг и обновление ответа
            parseAndUpdateResponse(conn, ip, jsonResponse, true);

            conn.commit();
            System.out.println("Данные успешно обновлены в БД!");

        } catch (Exception e) {
            handleRollback(conn, e);
        } finally {
            closeConnection(conn);
        }
    }

    private static void updateCoordinateRequestAndResponse(double latitude, double longitude, String jsonResponse) {
        Connection conn = null;
        try {
            conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD);
            conn.setAutoCommit(false);

            // Обновление данных в таблице coordinate_requests
            String updateRequestSQL = "UPDATE coordinate_requests SET request_timestamp = ?, raw_response = ? WHERE ST_Distance(location, ST_SetSRID(ST_MakePoint(?, ?), 4326)) < 0.0001";
            PreparedStatement requestStmt = conn.prepareStatement(updateRequestSQL);
            requestStmt.setTimestamp(1, Timestamp.valueOf(LocalDateTime.now()));
            requestStmt.setString(2, jsonResponse);
            requestStmt.setDouble(3, longitude);
            requestStmt.setDouble(4, latitude);
            requestStmt.executeUpdate();

            // Парсинг и обновление ответа
            parseAndUpdateResponse(conn, latitude, longitude, jsonResponse, false);

            conn.commit();
            System.out.println("Данные успешно обновлены в БД!");

        } catch (Exception e) {
            handleRollback(conn, e);
        } finally {
            closeConnection(conn);
        }
    }

    private static void parseAndUpdateResponse(Connection conn, String ip, String jsonResponse, boolean isIpRequest) throws Exception {
        JsonNode root = new ObjectMapper().readTree(jsonResponse);
        JsonNode featureMember = root.path("response").path("GeoObjectCollection").path("featureMember").get(0);
        JsonNode geoObject = featureMember.path("GeoObject");
        JsonNode metaData = geoObject.path("metaDataProperty").path("GeocoderMetaData");

        String countryCode = metaData.path("Address").path("country_code").asText();
        String countryName = "";
        String city = "";
        JsonNode components = metaData.path("Address").path("Components");
        for (JsonNode component : components) {
            String kind = component.path("kind").asText();
            if (kind.equals("country")) {
                countryName = component.path("name").asText();
            } else if (kind.equals("locality")) {
                city = component.path("name").asText();
            }
        }

        String[] coordinates = geoObject.path("Point").path("pos").asText().split(" ");
        double longitude = Double.parseDouble(coordinates[0]);
        double latitude = Double.parseDouble(coordinates[1]);

        String updateSQL;
        if (isIpRequest) {
            updateSQL = "UPDATE responses SET country_code = ?, country_name = ?, city = ?, location = ST_SetSRID(ST_MakePoint(?, ?), 4326), response_timestamp = ?, raw_response = ? WHERE request_id = (SELECT id FROM requests WHERE ip_address = ?)";
        } else {
            updateSQL = "UPDATE coordinate_responses SET country_code = ?, country_name = ?, city = ?, location = ST_SetSRID(ST_MakePoint(?, ?), 4326), response_timestamp = ?, raw_response = ? WHERE request_id = (SELECT id FROM coordinate_requests WHERE ST_Distance(location, ST_SetSRID(ST_MakePoint(?, ?), 4326)) < 0.0001)";
        }

        PreparedStatement responseStmt = conn.prepareStatement(updateSQL);
        responseStmt.setString(1, countryCode);
        responseStmt.setString(2, countryName);
        responseStmt.setString(3, city);
        responseStmt.setDouble(4, longitude);
        responseStmt.setDouble(5, latitude);
        responseStmt.setTimestamp(6, Timestamp.valueOf(LocalDateTime.now()));
        responseStmt.setString(7, jsonResponse);
        if (isIpRequest) {
            responseStmt.setString(8, ip);
        } else {
            responseStmt.setDouble(8, longitude);
            responseStmt.setDouble(9, latitude);
        }

        responseStmt.executeUpdate();
    }

    private static void parseAndUpdateResponse(Connection conn, double latitude, double longitude, String jsonResponse, boolean isIpRequest) throws Exception {
        parseAndUpdateResponse(conn, null, jsonResponse, isIpRequest);
    }

    private static void handleRollback(Connection conn, Exception e) {
        if (conn != null) {
            try {
                conn.rollback();
            } catch (SQLException ex) {
                ex.printStackTrace();
            }
        }
        e.printStackTrace();
    }

    private static void closeConnection(Connection conn) {
        if (conn != null) {
            try {
                conn.close();
            } catch (SQLException e) {
                e.printStackTrace();
            }
        }
    }
}