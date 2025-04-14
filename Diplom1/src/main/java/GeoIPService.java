package com.example;

import com.example.geoipservice.client.GeoCoderClient;
import com.example.geoipservice.database.DatabaseHandler;
import com.example.geoipservice.model.GeoData;
import com.example.geoipservice.model.RequestType;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.Scanner;

public class GeoIPService {

    private static final Logger logger = LoggerFactory.getLogger(GeoIPService.class);

    private final GeoCoderClient geoCoderClient;
    private final DatabaseHandler databaseHandler;

    public GeoIPService() {
        this.geoCoderClient = new GeoCoderClient();
        this.databaseHandler = new DatabaseHandler();
    }

    public static void main(String[] args) {
        GeoIPService service = new GeoIPService();
        Scanner scanner = new Scanner(System.in);

        while (true) {
            System.out.println("Выберите тип запроса:");
            System.out.println("1 - По IP-адресу");
            System.out.println("2 - По координатам");
            System.out.println("0 - Выход");
            int choice = scanner.nextInt();
            scanner.nextLine();

            switch (choice) {
                case 1:
                    service.handleIpRequest(scanner);
                    break;
                case 2:
                    service.handleCoordinateRequest(scanner);
                    break;
                case 0:
                    System.out.println("Выход из программы.");
                    return;
                default:
                    System.out.println("Неверный выбор.");
            }
        }
    }

    private void handleIpRequest(Scanner scanner) {
        try {
            System.out.print("Введите IP-адрес: ");
            String ipAddress = scanner.nextLine();

            GeoData geoData = databaseHandler.getDataByIp(ipAddress);
            if (geoData != null) {
                System.out.println("Данные из БД: " + geoData);
                if (databaseHandler.isDataOutdated(RequestType.IP, ipAddress, 0, 0)) {
                    logger.info("Данные устарели. Запрос к API для IP: {}", ipAddress);
                    String jsonResponse = geoCoderClient.getAddressByIp(ipAddress);
                    if (jsonResponse != null) {
                        geoData.setRawResponse(jsonResponse);
                        databaseHandler.updateRequestAndResponse(RequestType.IP, geoData);
                        geoData = databaseHandler.getDataByIp(ipAddress);
                        System.out.println("Данные обновлены из API: " + geoData);
                    } else {
                        logger.error("Не удалось получить данные от API для IP: {}", ipAddress);
                        System.out.println("Не удалось получить данные от API.");
                    }
                }
            } else {
                logger.info("Данные не найдены в БД. Запрос к API для IP: {}", ipAddress);
                String jsonResponse = geoCoderClient.getAddressByIp(ipAddress);
                if (jsonResponse != null) {
                    geoData = new GeoData();
                    geoData.setIp(ipAddress);
                    geoData.setRawResponse(jsonResponse);
                    databaseHandler.saveRequestAndResponse(RequestType.IP, geoData);
                    geoData = databaseHandler.getDataByIp(ipAddress);
                    System.out.println("Данные получены из API: " + geoData);
                } else {
                    logger.error("Не удалось получить данные от API для IP: {}", ipAddress);
                    System.out.println("Не удалось получить данные от API.");
                }
            }
        } catch (Exception e) {
            logger.error("Ошибка при обработке запроса по IP: {}", e.getMessage(), e);
            System.out.println("Произошла ошибка при обработке запроса.");
        }
    }

    private void handleCoordinateRequest(Scanner scanner) {
        try {
            System.out.print("Введите широту: ");
            double latitude = scanner.nextDouble();
            System.out.print("Введите долготу: ");
            double longitude = scanner.nextDouble();
            scanner.nextLine();

            GeoData geoData = databaseHandler.getDataByCoordinates(latitude, longitude);
            if (geoData != null) {
                System.out.println("Данные из БД: " + geoData);
                if (databaseHandler.isDataOutdated(RequestType.COORDINATES, null, latitude, longitude)) {
                    logger.info("Данные устарели. Запрос к API для координат: Широта={}, Долгота={}", latitude, longitude);
                    String jsonResponse = geoCoderClient.getAddressByCoordinates(latitude, longitude);
                    if (jsonResponse != null) {
                        geoData.setRawResponse(jsonResponse);
                        databaseHandler.updateRequestAndResponse(RequestType.COORDINATES, geoData);
                        geoData = databaseHandler.getDataByCoordinates(latitude, longitude);
                        System.out.println("Данные обновлены из API: " + geoData);
                    } else {
                        logger.error("Не удалось получить данные от API для координат: Широта={}, Долгота={}", latitude, longitude);
                        System.out.println("Не удалось получить данные от API.");
                    }
                }
            } else {
                logger.info("Данные не найдены в БД. Запрос к API для координат: Широта={}, Долгота={}", latitude, longitude);
                String jsonResponse = geoCoderClient.getAddressByCoordinates(latitude, longitude);
                if (jsonResponse != null) {
                    geoData = new GeoData();
                    geoData.setLatitude(latitude);
                    geoData.setLongitude(longitude);
                    geoData.setRawResponse(jsonResponse);
                    databaseHandler.saveRequestAndResponse(RequestType.COORDINATES, geoData);
                    geoData = databaseHandler.getDataByCoordinates(latitude, longitude);
                    System.out.println("Данные получены из API: " + geoData);
                } else {
                    logger.error("Не удалось получить данные от API для координат: Широта={}, Долгота={}", latitude, longitude);
                    System.out.println("Не удалось получить данные от API.");
                }
            }
        } else {
            logger.error("Ошибка при обработке запроса по координатам: {}", e.getMessage(), e);
            System.out.println("Произошла ошибка при обработке запроса.");
        }
    }
}