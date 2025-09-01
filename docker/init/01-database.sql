# create databases
CREATE DATABASE IF NOT EXISTS `demo`;

# create root user and grant rights
CREATE USER 'root'@'localhost' IDENTIFIED BY 'p@ssw0rd';
GRANT ALL ON *.* TO 'root'@'%';