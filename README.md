SQL Commads 

CREATE DATABASE xsddb;
USE xsddb; //these are not mandetory but user creation is mandetory

CREATE USER 'xsduser'@'localhost' IDENTIFIED BY '1234';


GRANT ALL PRIVILEGES ON *.* TO 'xsduser'@'localhost'; 
FLUSH PRIVILEGES;

DROP DATABASE xsddb;

Create the database first.


