package com.example.geoipservice.database;

import com.example.geoipservice.model.GeoData;
import com.example.geoipservice.model.RequestType;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.*;
import java.time.LocalDateTime;

public class DatabaseHandler {
    // ... (остальной код класса, включая поля и другие методы)

    private static final Logger logger = LoggerFactory.getLogger(DatabaseHandler.class);
    private static final String DB_URL = "jdbc:postgresql://localhost:5432/demo";
    private static final String DB_USER = "postgres";
    private static final String DB_PASSWORD = "123";

    // Метод для получения данных из БД по IP
    public GeoData getDataByIp(String ip) {
        String sql = "SELECT r.ip_address, res.country_name, res.city, ST_AsText(res.location) AS location, r.raw_response " +
                "FROM requests r " +
                "JOIN responses res ON r.id = res.request_id " +
                "WHERE r.ip_address = ?";
        return executeQuery(sql, ip, null, null, RequestType.IP);
    }

    // Метод для получения данных из БД по координатам
    public GeoData getDataByCoordinates(double latitude, double longitude) {
        String sql = "SELECT ST_X(cr.location) AS latitude, ST_Y(cr.location) AS longitude, " +
                "res.country_name, res.city, ST_AsText(cr.location) AS location, cr.raw_response " +
                "FROM coordinate_requests cr " +
                "JOIN coordinate_responses res ON cr.id = res.request_id " +
                "WHERE ST_Distance(cr.location, ST_SetSRID(ST_MakePoint(?, ?), 4326)) < 0.0001";
        return executeQuery(sql, null, latitude, longitude, RequestType.COORDINATES);
    }

    // Метод для сохранения запроса и ответа в БД
    public void saveRequestAndResponse(RequestType requestType, GeoData geoData) {
        if (requestType == RequestType.IP) {
            saveIpRequestAndResponse(geoData.getIp(), geoData.getRawResponse());
        } else if (requestType == RequestType.COORDINATES) {
            saveCoordinateRequestAndResponse(geoData.getLatitude(), geoData.getLongitude(), geoData.getRawResponse());
        }
    }

    private void saveIpRequestAndResponse(String ip, String jsonResponse) {
        Connection conn = null;
        try {
            conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD);
            conn.setAutoCommit(false);

            // Вставка в таблицу requests
            String insertRequestSQL = "INSERT INTO requests (ip_address, request_timestamp, raw_response) VALUES (?, ?, ?) RETURNING id";
            PreparedStatement requestStmt = conn.prepareStatement(insertRequestSQL);
            requestStmt.setString(1, ip);
            requestStmt.setTimestamp(2, Timestamp.valueOf(java.time.LocalDateTime.now()));
            requestStmt.setString(3, jsonResponse);
            ResultSet rs = requestStmt.executeQuery();
            rs.next();
            long requestId = rs.getLong(1);

            // Парсинг и сохранение ответа
            parseAndSaveResponse(conn, requestId, jsonResponse, false);

            conn.commit();
            logger.info("Данные успешно сохранены в БД для IP: {}", ip);

        } catch (SQLException e) {
            handleRollback(conn, e);
        } finally {
            closeConnection(conn);
        }
    }

    private void saveCoordinateRequestAndResponse(double latitude, double longitude, String jsonResponse) {
        Connection conn = null;
        try {
            conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD);
            conn.setAutoCommit(false);

            // Вставка в таблицу coordinate_requests
            String insertRequestSQL = "INSERT INTO coordinate_requests (location, request_timestamp, raw_response) VALUES (ST_SetSRID(ST_MakePoint(?, ?), 4326), ?, ?) RETURNING id";
            PreparedStatement requestStmt = conn.prepareStatement(insertRequestSQL);
            requestStmt.setDouble(1, longitude);
            requestStmt.setDouble(2, latitude);
            requestStmt.setTimestamp(3, Timestamp.valueOf(java.time.LocalDateTime.now()));
            requestStmt.setString(4, jsonResponse);
            ResultSet rs = requestStmt.executeQuery();
            rs.next();
            long requestId = rs.getLong(1);

            // Парсинг и сохранение ответа
            parseAndSaveResponse(conn, requestId, jsonResponse, true);

            conn.commit();
            logger.info("Данные успешно сохранены в БД для координат: Широта={}, Долгота={}", latitude, longitude);

        } catch (SQLException e) {
            handleRollback(conn, e);
        } finally {
            closeConnection(conn);
        }
    }

    // Метод для обновления данных в БД
    public void updateRequestAndResponse(RequestType requestType, GeoData geoData) {
        if (requestType == RequestType.IP) {
            updateIpRequestAndResponse(geoData.getIp(), geoData.getRawResponse());
        } else if (requestType == RequestType.COORDINATES) {
            updateCoordinateRequestAndResponse(geoData.getLatitude(), geoData.getLongitude(), geoData.getRawResponse());
        }
    }

    private void updateIpRequestAndResponse(String ip, String jsonResponse) {
        Connection conn = null;
        try {
            conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD);
            conn.setAutoCommit(false);

            // Обновление данных в таблице requests
            String updateRequestSQL = "UPDATE requests SET request_timestamp = ?, raw_response = ? WHERE ip_address = ?";
            PreparedStatement requestStmt = conn.prepareStatement(updateRequestSQL);
            requestStmt.setTimestamp(1, Timestamp.valueOf(java.time.LocalDateTime.now()));
            requestStmt.setString(2, jsonResponse);
            requestStmt.setString(3, ip);
            requestStmt.executeUpdate();

            // Парсинг и обновление ответа
            parseAndUpdateResponse(conn, ip, jsonResponse, true);

            conn.commit();
            logger.info("Данные успешно обновлены в БД для IP: {}", ip);

        } catch (SQLException e) {
            handleRollback(conn, e);
        } finally {
            closeConnection(conn);
        }
    }

    private void updateCoordinateRequestAndResponse(double latitude, double longitude, String jsonResponse) {
        Connection conn = null;
        try {
            conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD);
            conn.setAutoCommit(false);

            // Обновление данных в таблице coordinate_requests
            String updateRequestSQL = "UPDATE coordinate_requests SET request_timestamp = ?, raw_response = ? WHERE ST_Distance(location, ST_SetSRID(ST_MakePoint(?, ?), 4326)) < 0.0001";
            PreparedStatement requestStmt = conn.prepareStatement(updateRequestSQL);
            requestStmt.setTimestamp(1, Timestamp.valueOf(java.time.LocalDateTime.now()));
            requestStmt.setString(2, jsonResponse);
            requestStmt.setDouble(3, longitude);
            requestStmt.setDouble(4, latitude);
            requestStmt.executeUpdate();

            // Парсинг и обновление ответа
            parseAndUpdateResponse(conn, latitude, longitude, jsonResponse, false);

            conn.commit();
            logger.info("Данные успешно обновлены в БД для координат: Широта={}, Долгота={}", latitude, longitude);

        } catch (SQLException e) {
            handleRollback(conn, e);
        } finally {
            closeConnection(conn);
        }
    }
    // Метод для проверки актуальности данных
    public boolean isDataOutdated(RequestType requestType, String ip, double latitude, double longitude) {
        String sql;
        if (requestType == RequestType.IP) {
            sql = "SELECT request_timestamp FROM requests WHERE ip_address = ? ORDER BY request_timestamp DESC LIMIT 1";
        } else {
            sql = "SELECT request_timestamp FROM coordinate_requests WHERE ST_Distance(location, ST_SetSRID(ST_MakePoint(?, ?), 4326)) < 0.0001 ORDER BY request_timestamp DESC LIMIT 1";
        }
        return checkDataOutdated(sql, ip, latitude, longitude, requestType);
    }

    private boolean checkDataOutdated(String sql, String ip, double latitude, double longitude, RequestType requestType) {
        try (Connection conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD);
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            if (requestType == RequestType.IP) {
                stmt.setString(1, ip);
                logger.debug("Проверка свежести данных для IP: {}", ip);
            } else {
                stmt.setDouble(1, longitude);
                stmt.setDouble(2, latitude);
                logger.debug("Проверка свежести данных для координат: Широта={}, Долгота={}", latitude, longitude);
            }

            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                Timestamp timestamp = rs.getTimestamp("request_timestamp");
                java.time.LocalDateTime lastUpdate = timestamp.toLocalDateTime();
                java.time.LocalDateTime now = java.time.LocalDateTime.now();
                long daysSinceLastUpdate = java.time.temporal.ChronoUnit.DAYS.between(lastUpdate, now);

                logger.debug("Последнее обновление: {}", lastUpdate);
                logger.debug("Текущее время: {}", now);
                logger.debug("Дней с последнего обновления: {}", daysSinceLastUpdate);

                if (daysSinceLastUpdate > 30) {
                    logger.info("Данные устарели (прошло более 30 дней).");
                    return true;
                } else {
                    logger.info("Данные актуальны (прошло {} дней).", daysSinceLastUpdate);
                    return false;
                }
            } else {
                logger.info("Данные не найдены в базе. Считаем их устаревшими.");
                return true;
            }
        } catch (SQLException e) {
            logger.error("Ошибка при выполнении SQL-запроса: {}", e.getMessage(), e);
            return true; // В случае ошибки считаем данные устаревшими
        }
    }

    private GeoData executeQuery(String sql, String ip, Double latitude, Double longitude, RequestType requestType) {
        try (Connection conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD);
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            if (requestType == RequestType.IP) {
                stmt.setString(1, ip);
            } else if (requestType == RequestType.COORDINATES) {
                stmt.setDouble(1, longitude);
                stmt.setDouble(2, latitude);
            }

            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                GeoData geoData = new GeoData();
                if (requestType == RequestType.IP) {
                    geoData.setIp(rs.getString("ip_address"));
                } else if (requestType == RequestType.COORDINATES) {
                    geoData.setLatitude(rs.getDouble("latitude"));
                    geoData.setLongitude(rs.getDouble("longitude"));
                }
                geoData.setCountryName(rs.getString("country_name"));
                geoData.setCity(rs.getString("city"));
                //  Вам может потребоваться дополнительная обработка для извлечения координат из строки location
                geoData.setLocation(rs.getString("location"));
                geoData.setRawResponse(rs.getString("raw_response"));
                return geoData;
            }
        } catch (SQLException e) {
            logger.error("Ошибка при выполнении SQL-запроса: {}", e.getMessage(), e);
        }
        return null;
    }

    private void parseAndSaveResponse(Connection conn, long requestId, String jsonResponse, boolean isCoordinate) throws SQLException {
        try {
            ObjectMapper mapper = new ObjectMapper();
            JsonNode root = mapper.readTree(jsonResponse);
            JsonNode geoObject = root.path("response").path("GeoObjectCollection").path("featureMember").path(0).path("GeoObject");
            if (geoObject.isMissingNode()) {
                logger.warn("Не удалось распарсить ответ для requestId {}: {}", requestId, jsonResponse);
                return;
            }

            String countryCode = geoObject.path("metaDataProperty").path("GeocoderMetaData").path("AddressDetails").path("Country").path("CountryNameCode").asText();
            String countryName = geoObject.path("metaDataProperty").path("GeocoderMetaData").path("AddressDetails").path("Country").path("CountryName").asText();
            // Use AdministrativeAreaName if LocalityName is missing
            JsonNode localityNode = geoObject.path("metaDataProperty").path("GeocoderMetaData").path("AddressDetails").path("Locality");
            String city = localityNode.isMissingNode() ?
                    geoObject.path("metaDataProperty").path("GeocoderMetaData").path("AddressDetails").path("AdministrativeArea").path("AdministrativeAreaName").asText()
                    : localityNode.path("LocalityName").asText();

            String insertResponseSQL = "INSERT INTO responses (request_id, country_code, country_name, city, response_timestamp, raw_response, location) " +
                    "VALUES (?, ?, ?, ?, ?, ?, ST_SetSRID(ST_MakePoint(?, ?), 4326))";

            if (isCoordinate) {
                String pos = geoObject.path("Point").path("pos").asText();
                String[] coordinates = pos.split(" ");
                double longitude = Double.parseDouble(coordinates[0]);
                double latitude = Double.parseDouble(coordinates[1]);
                insertResponseSQL = "INSERT INTO coordinate_responses (request_id, country_code, country_name, city, response_timestamp, raw_response, location) " +
                        "VALUES (?, ?, ?, ?, ?, ?, ST_SetSRID(ST_MakePoint(?, ?), 4326))";
                try (PreparedStatement responseStmt = conn.prepareStatement(insertResponseSQL)) {
                    responseStmt.setLong(1, requestId);
                    responseStmt.setString(2, countryCode);
                    responseStmt.setString(3, countryName);
                    responseStmt.setString(4, city);
                    responseStmt.setTimestamp(5, Timestamp.valueOf(LocalDateTime.now()));
                    responseStmt.setString(6, jsonResponse);
                    responseStmt.setDouble(7, longitude);
                    responseStmt.setDouble(8, latitude);
                    responseStmt.executeUpdate();
                }
            } else {
                try (PreparedStatement responseStmt = conn.prepareStatement(insertResponseSQL)) {
                    responseStmt.setLong(1, requestId);
                    responseStmt.setString(2, countryCode);
                    responseStmt.setString(3, countryName);
                    responseStmt.setString(4, city);
                    responseStmt.setTimestamp(5, Timestamp.valueOf(LocalDateTime.now()));
                    responseStmt.setString(6, jsonResponse);
                    responseStmt.setNull(7, Types.OTHER); //  location = NULL for IP requests
                    responseStmt.setNull(8, Types.OTHER); //  location = NULL for IP requests
                    responseStmt.executeUpdate();
                }
            }
        } catch (Exception e) {
            logger.error("Ошибка при парсинге и сохранении ответа: {}", e.getMessage(), e);
            throw new SQLException("Ошибка при парсинге ответа", e);
        }
    }

    private void parseAndUpdateResponse(Connection conn, String ip, String jsonResponse, boolean isIpRequest) throws SQLException {
        try {
            ObjectMapper mapper = new ObjectMapper();
            JsonNode root = mapper.readTree(jsonResponse);
            JsonNode geoObject = root.path("response").path("GeoObjectCollection").path("featureMember").path(0).path("GeoObject");
            if (geoObject.isMissingNode()) {
                logger.warn("Не удалось распарсить ответ для IP {}: {}", ip, jsonResponse);
                return;
            }

            String countryCode = geoObject.path("metaDataProperty").path("GeocoderMetaData").path("AddressDetails").path("Country").path("CountryNameCode").asText();
            String countryName = geoObject.path("metaDataProperty").path("GeocoderMetaData").path("AddressDetails").path("Country").path("CountryName").asText();
            // Use AdministrativeAreaName if LocalityName is missing
            JsonNode localityNode = geoObject.path("metaDataProperty").path("GeocoderMetaData").path("AddressDetails").path("Locality");
            String city = localityNode.isMissingNode() ?
                    geoObject.path("metaDataProperty").path("GeocoderMetaData").path("AddressDetails").path("AdministrativeArea").path("AdministrativeAreaName").asText()
                    : localityNode.path("LocalityName").asText();

            String updateResponseSQL = "UPDATE responses SET country_code = ?, country_name = ?, city = ?, response_timestamp = ?, raw_response = ? " +
                    "WHERE request_id IN (SELECT id FROM requests WHERE ip_address = ?)";

            try (PreparedStatement responseStmt = conn.prepareStatement(updateResponseSQL)) {
                responseStmt.setString(1, countryCode);
                responseStmt.setString(2, countryName);
                responseStmt.setString(3, city);
                responseStmt.setTimestamp(4, Timestamp.valueOf(LocalDateTime.now()));
                responseStmt.setString(5, jsonResponse);
                responseStmt.setString(6, ip);
                responseStmt.executeUpdate();
            }
        } catch (Exception e) {
            logger.error("Ошибка при парсинге и обновлении ответа для IP {}: {}", ip, e.getMessage(), e);
            throw new SQLException("Ошибка при парсинге ответа", e);
        }
    }

    private void parseAndUpdateResponse(Connection conn, double latitude, double longitude, String jsonResponse, boolean isIpRequest) throws SQLException {
        try {
            ObjectMapper mapper = new ObjectMapper();
            JsonNode root = mapper.readTree(jsonResponse);
            JsonNode geoObject = root.path("response").path("GeoObjectCollection").path("featureMember").path(0).path("GeoObject");
            if (geoObject.isMissingNode()) {
                logger.warn("Не удалось распарсить ответ для координат {}: {}", latitude + ", " + longitude, jsonResponse);
                return;
            }

            String countryCode = geoObject.path("metaDataProperty").path("GeocoderMetaData").path("AddressDetails").path("Country").path("CountryNameCode").asText();
            String countryName = geoObject.path("metaDataProperty").path("GeocoderMetaData").path("AddressDetails").path("Country").path("CountryName").asText();
            // Use AdministrativeAreaName if LocalityName is missing
            JsonNode localityNode = geoObject.path("metaDataProperty").path("GeocoderMetaData").path("AddressDetails").path("Locality");
            String city = localityNode.isMissingNode() ?
                    geoObject.path("metaDataProperty").path("GeocoderMetaData").path("AddressDetails").path("AdministrativeArea").path("AdministrativeAreaName").asText()
                    : localityNode.path("LocalityName").asText();

            String updateResponseSQL = "UPDATE coordinate_responses SET country_code = ?, country_name = ?, city = ?, response_timestamp = ?, raw_response = ? " +
                    "WHERE request_id IN (SELECT id FROM coordinate_requests WHERE ST_Distance(location, ST_SetSRID(ST_MakePoint(?, ?), 4326)) < 0.0001)";
            String pos = geoObject.path("Point").path("pos").asText();
            String[] coordinates = pos.split(" ");
            double newLongitude = Double.parseDouble(coordinates[0]);
            double newLatitude = Double.parseDouble(coordinates[1]);
            try (PreparedStatement responseStmt = conn.prepareStatement(updateResponseSQL)) {
                responseStmt.setString(1, countryCode);
                responseStmt.setString(2, countryName);
                responseStmt.setString(3, city);
                responseStmt.setTimestamp(4, Timestamp.valueOf(LocalDateTime.now()));
                responseStmt.setString(5, jsonResponse);
                responseStmt.setDouble(6, newLongitude);
                responseStmt.setDouble(7, newLatitude);
                responseStmt.executeUpdate();
            }
        } catch (Exception e) {
            logger.error("Ошибка при парсинге и обновлении ответа для координат {}: {}", latitude + ", " + longitude, e.getMessage(), e);
            throw new SQLException("Ошибка при парсинге ответа", e);
        }
    }

    // ... (остальные методы)
}