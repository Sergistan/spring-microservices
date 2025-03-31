package com.utochkin.historyservice.models;

public record Address(String city,
                      String street,
                      Integer houseNumber,
                      Integer apartmentNumber) {

}
