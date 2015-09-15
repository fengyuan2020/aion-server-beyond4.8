package com.aionemu.loginserver.dao;

import java.util.List;

import com.aionemu.commons.database.dao.DAO;
import com.aionemu.loginserver.taskmanager.trigger.TaskFromDBTrigger;

/**
 * @author Divinity, nrg
 */
public abstract class TaskFromDBDAO implements DAO {

	/**
	 * Return all tasks from DB
	 * 
	 * @return all tasks
	 */
	public abstract List<TaskFromDBTrigger> getAllTasks();

	/**
	 * Returns class name that will be uses as unique identifier for all DAO classes
	 * 
	 * @return class name
	 */
	@Override
	public final String getClassName() {
		return TaskFromDBDAO.class.getName();
	}
}
