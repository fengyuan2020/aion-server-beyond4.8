package com.aionemu.commons.configs;

import java.io.File;

import com.aionemu.commons.configuration.Property;

/**
 * This class holds all configuration of database
 * 
 * @author SoulKeeper
 */
public class DatabaseConfig {

	/**
	 * Fully qualified name of the database driver
	 */
	@Property(key = "database.driver")
	public static Class<?> DATABASE_DRIVER;

	@Property(key = "database.url")
	public static String DATABASE_URL;

	@Property(key = "database.user")
	public static String DATABASE_USER;

	@Property(key = "database.password")
	public static String DATABASE_PASSWORD;

	/**
	 * Maximum amount of connections kept in connection pool
	 */
	@Property(key = "database.connectionpool.connections.max", defaultValue = "5")
	public static int DATABASE_CONNECTIONS_MAX;
	/**
	 * Maximum wait time when getting a DB connection, before throwing a timeout error
	 */
	@Property(key = "database.connectionpool.timeout", defaultValue = "5000")
	public static int DATABASE_TIMEOUT;

	/**
	 * Location of DAO .java implementations
	 */
	@Property(key = "database.dao_directory", defaultValue = "./data/handlers/mysql5")
	public static File DAO_DIRECTORY;

}
