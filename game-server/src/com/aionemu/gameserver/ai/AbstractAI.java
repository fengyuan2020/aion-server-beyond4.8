package com.aionemu.gameserver.ai;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import com.aionemu.gameserver.ai.event.AIEventLog;
import com.aionemu.gameserver.ai.event.AIEventType;
import com.aionemu.gameserver.ai.event.AIListenable;
import com.aionemu.gameserver.ai.handler.FreezeEventHandler;
import com.aionemu.gameserver.configs.main.AIConfig;
import com.aionemu.gameserver.events.AbstractEventSource;
import com.aionemu.gameserver.model.gameobjects.Creature;
import com.aionemu.gameserver.model.gameobjects.VisibleObject;
import com.aionemu.gameserver.model.gameobjects.player.Player;
import com.aionemu.gameserver.model.stats.calc.Stat2;
import com.aionemu.gameserver.model.templates.item.ItemAttackType;
import com.aionemu.gameserver.model.templates.npcshout.ShoutEventType;
import com.aionemu.gameserver.model.templates.spawns.SpawnTemplate;
import com.aionemu.gameserver.skillengine.model.Effect;
import com.aionemu.gameserver.spawnengine.SpawnEngine;
import com.aionemu.gameserver.utils.annotations.AnnotatedMethod;
import com.aionemu.gameserver.world.WorldPosition;

/**
 * @author ATracer
 */
public abstract class AbstractAI<T extends Creature> extends AbstractEventSource<GeneralAIEvent> implements AI {

	private final T owner;
	private AIState currentState;
	private AISubState currentSubState;
	private static volatile Map<Class<?>, Map<AIEventType, Method>> listenableMethodsByClass;

	private final Lock thinkLock = new ReentrantLock();

	private boolean logging = false;

	private volatile AIEventLog eventLog;

	protected AbstractAI(T owner) {
		this.owner = owner;
		this.currentState = AIState.CREATED;
		this.currentSubState = AISubState.NONE;

		if (isFirstMethodFill) {
			Map<AIEventType, Method> listenableMethods = listenableMethodsByClass.get(getClass());
			if (listenableMethods != null) {
				// Clean null values after the method map was filled in
				Iterator<Method> iter = listenableMethods.values().iterator();
				while (iter.hasNext()) {
					if (iter.next() == null)
						iter.remove();
				}
			}
		}
	}

	public AIEventLog getEventLog() {
		return eventLog;
	}

	@Override
	public AIState getState() {
		return currentState;
	}

	public final boolean isInState(AIState state) {
		return currentState == state;
	}

	@Override
	public AISubState getSubState() {
		return currentSubState;
	}

	public final boolean isInSubState(AISubState subState) {
		return currentSubState == subState;
	}

	@Override
	public String getName() {
		if (getClass().isAnnotationPresent(AIName.class)) {
			AIName annotation = getClass().getAnnotation(AIName.class);
			return annotation.value();
		}
		return "noname";
	}

	protected boolean canHandleEvent(AIEventType eventType) {
		switch (eventType) {
			case CREATURE_MOVED:
			case DIALOG_START:
			case DIALOG_FINISH:
				return currentState == AIState.IDLE || currentState == AIState.WALKING;
		}
		return true;
	}

	public synchronized boolean setStateIfNot(AIState newState) {
		if (this.currentState == newState)
			return false;

		if (isLogging()) {
			AILogger.info(this, "Setting AI state to " + newState);
			if (this.currentState == AIState.DIED && newState == AIState.FIGHT) {
				StackTraceElement[] stack = new Throwable().getStackTrace();
				for (StackTraceElement elem : stack)
					AILogger.info(this, elem.toString());
			}
		}
		this.currentState = newState;
		return true;
	}

	public synchronized boolean setSubStateIfNot(AISubState newSubState) {
		if (this.currentSubState == newSubState) {
			if (isLogging()) {
				AILogger.info(this, "Can't change substate to " + newSubState + " from " + currentSubState);
			}
			return false;
		}
		if (isLogging()) {
			AILogger.info(this, "Setting AI substate to " + newSubState);
		}
		this.currentSubState = newSubState;
		return true;
	}

	@Override
	public final void onGeneralEvent(AIEventType event) {
		if (currentState.canHandle(event) && canHandleEvent(event)) {
			if (isLogging()) {
				AILogger.info(this, "General event " + event);
			}
			handleGeneralEvent(event);
		}
	}

	@Override
	public final void onCreatureEvent(AIEventType event, Creature creature) {
		Objects.requireNonNull(creature, "Creature must not be null");
		if (currentState.canHandle(event) && canHandleEvent(event)) {
			if (isLogging()) {
				AILogger.info(this, "Creature event " + event + ": " + creature.getObjectTemplate().getTemplateId());
			}
			handleCreatureEvent(event, creature);
		}
	}

	@Override
	public final void onCustomEvent(int eventId, Object... args) {
		if (isLogging()) {
			AILogger.info(this, "Custom event - id = " + eventId);
		}
		handleCustomEvent(eventId, args);
	}

	/**
	 * Will be hidden for all AI's below NpcAI
	 * 
	 * @return
	 */
	public T getOwner() {
		return owner;
	}

	public int getObjectId() {
		return owner.getObjectId();
	}

	public WorldPosition getPosition() {
		return owner.getPosition();
	}

	public VisibleObject getTarget() {
		return owner.getTarget();
	}

	public boolean isDead() {
		return owner.isDead();
	}

	public final boolean tryLockThink() {
		return thinkLock.tryLock();
	}

	public final void unlockThink() {
		thinkLock.unlock();
	}

	@Override
	public final boolean isLogging() {
		return logging;
	}

	public void setLogging(boolean logging) {
		this.logging = logging;
	}

	@AIListenable(enabled = false, type = AIEventType.ACTIVATE)
	protected abstract void handleActivate();

	@AIListenable(enabled = false, type = AIEventType.DEACTIVATE)
	protected abstract void handleDeactivate();

	@AIListenable(enabled = false, type = AIEventType.BEFORE_SPAWNED)
	protected abstract void handleBeforeSpawned();

	@AIListenable(enabled = false, type = AIEventType.SPAWNED)
	protected abstract void handleSpawned();

	@AIListenable(enabled = false, type = AIEventType.DESPAWNED)
	protected abstract void handleDespawned();

	@AIListenable(enabled = false, type = AIEventType.DIED)
	protected abstract void handleDied();

	@AIListenable(enabled = false, type = AIEventType.MOVE_VALIDATE)
	protected abstract void handleMoveValidate();

	@AIListenable(enabled = false, type = AIEventType.MOVE_ARRIVED)
	protected abstract void handleMoveArrived();

	@AIListenable(enabled = false, type = AIEventType.ATTACK_COMPLETE)
	protected abstract void handleAttackComplete();

	@AIListenable(enabled = false, type = AIEventType.ATTACK_FINISH)
	protected abstract void handleFinishAttack();

	@AIListenable(enabled = false, type = AIEventType.TARGET_TOOFAR)
	protected abstract void handleTargetTooFar();

	@AIListenable(enabled = false, type = AIEventType.TARGET_GIVEUP)
	protected abstract void handleTargetGiveup();

	@AIListenable(enabled = false, type = AIEventType.NOT_AT_HOME)
	protected abstract void handleNotAtHome();

	@AIListenable(enabled = false, type = AIEventType.BACK_HOME)
	protected abstract void handleBackHome();

	@AIListenable(enabled = false, type = AIEventType.DROP_REGISTERED)
	protected abstract void handleDropRegistered();

	@AIListenable(enabled = false, type = AIEventType.ATTACK)
	protected abstract void handleAttack(Creature creature);

	@AIListenable(enabled = false, type = AIEventType.CREATURE_NEEDS_HELP)
	protected abstract void creatureNeedsHelp(Creature creature);

	protected abstract boolean handleCreatureNeedsSupport(Creature creature);

	protected abstract boolean handleGuardAgainstAttacker(Creature creature);

	@AIListenable(enabled = false, type = AIEventType.CREATURE_SEE)
	protected abstract void handleCreatureSee(Creature creature);

	@AIListenable(enabled = false, type = AIEventType.CREATURE_NOT_SEE)
	protected abstract void handleCreatureNotSee(Creature creature);

	@AIListenable(enabled = false, type = AIEventType.CREATURE_MOVED)
	protected abstract void handleCreatureMoved(Creature creature);

	@AIListenable(enabled = false, type = AIEventType.CREATURE_AGGRO)
	protected abstract void handleCreatureAggro(Creature creature);

	@AIListenable(enabled = false, type = AIEventType.TARGET_CHANGED)
	protected abstract void handleTargetChanged(Creature creature);

	@AIListenable(enabled = false, type = AIEventType.FOLLOW_ME)
	protected abstract void handleFollowMe(Creature creature);

	@AIListenable(enabled = false, type = AIEventType.STOP_FOLLOW_ME)
	protected abstract void handleStopFollowMe(Creature creature);

	@AIListenable(enabled = false, type = AIEventType.DIALOG_START)
	protected abstract void handleDialogStart(Player player);

	@AIListenable(enabled = false, type = AIEventType.DIALOG_FINISH)
	protected abstract void handleDialogFinish(Player player);

	protected abstract void handleCustomEvent(int eventId, Object... args);

	public abstract boolean onPatternShout(ShoutEventType event, String pattern, int skillNumber);

	@Override
	protected final boolean addListenable(AnnotatedMethod annotatedMethod) {
		Annotation annotation = annotatedMethod.getAnnotation(AIListenable.class);
		if (annotation instanceof AIListenable) {
			AIListenable listenable = (AIListenable) annotation;
			if (listenableMethodsByClass == null)
				listenableMethodsByClass = new HashMap<>();
			Map<AIEventType, Method> listenableMethods = listenableMethodsByClass.get(getClass());
			if (listenableMethods == null) {
				// won't be called again because is cached by the AnnotationManager
				listenableMethods = new HashMap<>();
				listenableMethodsByClass.put(getClass(), listenableMethods);
			}
			// The first method added is at the top of class inheritance hierarchy,
			// so, if not enabled we add null value and others won't be added again
			if (listenableMethods.containsKey(listenable.type()))
				return false;
			if (!listenable.enabled()) {
				listenableMethods.put(listenable.type(), null);
				return false;
			}
			listenableMethods.put(listenable.type(), annotatedMethod.getMethod());
			return true;
		}
		return false;
	}

	@Override
	protected final boolean canHaveEventNotifications(GeneralAIEvent event) {
		return canHaveEventNotifications(event.getEventType());
	}

	public final boolean canHaveEventNotifications(AIEventType event) {
		if (listenableMethodsByClass == null)
			return false;
		Map<AIEventType, Method> listenableMethods = listenableMethodsByClass.get(getClass());
		return listenableMethods != null && listenableMethods.containsKey(event);
	}

	protected void handleGeneralEvent(AIEventType event) {
		if (isLogging()) {
			AILogger.info(this, "Handle general event " + event);
		}
		logEvent(event);

		GeneralAIEvent evObj = null;
		if (hasSubscribers()) {
			evObj = new GeneralAIEvent(this, event);
			if (!super.fireBeforeEvent(evObj))
				evObj = null;
		}

		switch (event) {
			case MOVE_VALIDATE:
				handleMoveValidate();
				break;
			case MOVE_ARRIVED:
				handleMoveArrived();
				break;
			case SPAWNED:
				handleSpawned();
				break;
			case BEFORE_SPAWNED:
				handleBeforeSpawned();
				break;
			case DESPAWNED:
				handleDespawned();
				break;
			case DIED:
				handleDied();
				break;
			case ATTACK_COMPLETE:
				handleAttackComplete();
				break;
			case ATTACK_FINISH:
				handleFinishAttack();
				break;
			case TARGET_TOOFAR:
				handleTargetTooFar();
				break;
			case TARGET_GIVEUP:
				handleTargetGiveup();
				break;
			case NOT_AT_HOME:
				handleNotAtHome();
				break;
			case BACK_HOME:
				handleBackHome();
				break;
			case ACTIVATE:
				handleActivate();
				break;
			case DEACTIVATE:
				handleDeactivate();
				break;
			case FREEZE:
				FreezeEventHandler.onFreeze(this);
				break;
			case UNFREEZE:
				FreezeEventHandler.onUnfreeze(this);
				break;
			case DROP_REGISTERED:
				handleDropRegistered();
				break;
		}

		if (evObj != null) {
			super.fireAfterEvent(evObj);
		}
	}

	/**
	 * @param event
	 */
	protected void logEvent(AIEventType event) {
		if (AIConfig.EVENT_DEBUG) {
			if (eventLog == null) {
				synchronized (this) {
					if (eventLog == null) {
						eventLog = new AIEventLog(10);
					}
				}
			}
			eventLog.addFirst(event);
		}
	}

	void handleCreatureEvent(AIEventType event, Creature creature) {
		switch (event) {
			case ATTACK:
				handleAttack(creature);
				logEvent(event);
				break;
			case CREATURE_NEEDS_SUPPORT:
				if (!handleCreatureNeedsSupport(creature)) {
					if (creature.getTarget() instanceof Creature) {
						if (!handleCreatureNeedsSupport((Creature) creature.getTarget()) && !handleGuardAgainstAttacker(creature))
							handleGuardAgainstAttacker((Creature) creature.getTarget());
					}
				}
				logEvent(event);
				break;
			case CREATURE_NEEDS_HELP:
				creatureNeedsHelp(creature);
				break;
			case CREATURE_SEE:
				handleCreatureSee(creature);
				break;
			case CREATURE_NOT_SEE:
				handleCreatureNotSee(creature);
				break;
			case CREATURE_MOVED:
				handleCreatureMoved(creature);
				break;
			case CREATURE_AGGRO:
				handleCreatureAggro(creature);
				logEvent(event);
				break;
			case TARGET_CHANGED:
				handleTargetChanged(creature);
				break;
			case FOLLOW_ME:
				handleFollowMe(creature);
				logEvent(event);
				break;
			case STOP_FOLLOW_ME:
				handleStopFollowMe(creature);
				logEvent(event);
				break;
			case DIALOG_START:
				handleDialogStart((Player) creature);
				logEvent(event);
				break;
			case DIALOG_FINISH:
				handleDialogFinish((Player) creature);
				logEvent(event);
				break;
		}
	}

	public abstract AttackIntention chooseAttackIntention();

	@Override
	public boolean onDialogSelect(Player player, int dialogActionId, int questId, int extendedRewardIndex) {
		return false;
	}

	/**
	 * Spawn object in the same world and instance as AI's owner
	 */
	protected VisibleObject spawn(int npcId, float x, float y, float z, byte heading) {
		return spawn(owner.getWorldId(), npcId, x, y, z, heading, 0, owner.getObjectId(), getPosition().getInstanceId());
	}

	/**
	 * Spawn object with staticId in the same world and instance as AI's owner
	 */
	protected VisibleObject spawn(int npcId, float x, float y, float z, byte heading, int staticId) {
		return spawn(owner.getWorldId(), npcId, x, y, z, heading, staticId, owner.getObjectId(), getPosition().getInstanceId());
	}

	protected VisibleObject spawn(int worldId, int npcId, float x, float y, float z, byte heading, int staticId, int creatorId, int instanceId) {
		SpawnTemplate template = SpawnEngine.newSingleTimeSpawn(worldId, npcId, x, y, z, heading, creatorId);
		template.setStaticId(staticId);
		return SpawnEngine.spawnObject(template, instanceId);
	}

	@Override
	public int modifyDamage(Creature attacker, int damage, Effect effect) {
		return damage;
	}

	@Override
	public int modifyOwnerDamage(int damage, Effect effect) {
		return damage;
	}

	@Override
	public void modifyOwnerStat(Stat2 stat) {
	}

	@Override
	public ItemAttackType modifyAttackType(ItemAttackType type) {
		return type;
	}

	@Override
	public int modifyAggroRange(int value) {
		return value;
	}
}
