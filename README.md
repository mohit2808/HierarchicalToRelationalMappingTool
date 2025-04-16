SQL Commads 

CREATE DATABASE xsddb;
USE xsddb;

CREATE USER 'xsduser'@'localhost' IDENTIFIED BY '1234';


GRANT ALL PRIVILEGES ON xsddb.* TO 'xsduser'@'localhost';
FLUSH PRIVILEGES;

DROP DATABASE xsddb;

Create the database first.
