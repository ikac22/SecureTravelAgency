drop table if exists users;
drop table if exists hashedUsers;
drop table if exists person;
drop table if exists country;
drop table if exists city;
drop table if exists hotel;
drop table if exists tags;
drop table if exists roomType;
drop table if exists reservation;
drop table if exists pricing_formula;
drop table if exists ratings;
drop table if exists roles;
drop table if exists permissions;
drop table if exists user_to_roles;
drop table if exists role_to_permissions;


create table users
(
    id       int          NOT NULL AUTO_INCREMENT,
    username varchar(255) NOT NULL,
    password varchar(255) NOT NULL,
    PRIMARY KEY (ID)
);

create table hashedUsers
(
    id           int          NOT NULL AUTO_INCREMENT,
    username     varchar(255) not null,
    passwordHash varchar(64)  not null,
    salt         varchar(64)  not null,
    totpKey      varchar(255) null,
    PRIMARY KEY (ID)
);

create table persons
(
    id        int          NOT NULL AUTO_INCREMENT PRIMARY KEY,
    firstName varchar(255) NOT NULL,
    lastName  varchar(255) NOT NULL,
    email     varchar(255) NOT NULL,
    PRIMARY KEY (ID)
);

create table country
(
    id   int          NOT NULL AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    CONSTRAINT uq_country_name UNIQUE (name)
);

create table city
(
    id        int          NOT NULL AUTO_INCREMENT PRIMARY KEY,
    countryId int          NOT NULL,
    name      varchar(255) NOT NULL
);

create table hotel
(
    id          int          NOT NULL AUTO_INCREMENT PRIMARY KEY,
    cityId      int          NOT NULL,
    name        varchar(200) NOT NULL,
    description varchar(511) NOT NULL,
    address     varchar(255) NOT NULL
);

CREATE TABLE roomType
(
    id            int            NOT NULL AUTO_INCREMENT PRIMARY KEY,
    hotelId       int            NOT NULL,
    name          varchar(100)   NOT NULL, -- e.g. "Double", "Suite"
    capacity      int            NOT NULL,
    pricePerNight decimal(12, 2) NOT NULL,
    totalRooms    int            NOT NULL
);

CREATE TABLE reservation
(
    id          int            NOT NULL AUTO_INCREMENT PRIMARY KEY,
    userId      int            NOT NULL,
    hotelId     int            NOT NULL,
    roomTypeId  int            NOT NULL,
    startDate   date           NOT NULL,
    endDate     date           NOT NULL,
    roomsCount  int            NOT NULL,
    guestsCount int            NOT NULL,
    totalPrice  decimal(12, 2) NOT NULL
);

create table pricing_formula
(
    id      int          NOT NULL PRIMARY KEY,
    formula varchar(500) NOT NULL
);

create table ratings
(
    hotelId int NOT NULL,
    userId  int NOT NULL,
    rating  int NOT NULL
);

create table user_to_roles
(
    userId int NOT NULL,
    roleId int NOT NULL
);

create table roles
(
    id   int          NOT NULL,
    name varchar(255) NOT NULL,
    PRIMARY KEY (ID)
);

create table permissions
(
    id   int          NOT NULL,
    name varchar(255) NOT NULL,
    PRIMARY KEY (ID)
);

create table role_to_permissions
(
    roleId       int NOT NULL,
    permissionId int NOT NULL
);