public int getCurrentGoalPercentage() { return currentGoalPercentage; }
public double getSuccessRate() { int total = attempts.get() + successes.get(); return total > 0 ? (successes.get() * 100.0 / total) : 0; }
public long getEtcsObtained() { if (activeModule instanceof com.vxv.chatbet.module.PickpocketingModule) return ((com.vxv.chatbet.module.PickpocketingModule) activeModule).getDodgyConsumed().get(); return etcsObtained.get(); }
public double getEstimatedEtcsToGoal() { return 0; }
public double getExpectedEtcs() { return 0; }
public long getAttemptsSinceLastEtc() { if (activeModule instanceof com.vxv.chatbet.module.PickpocketingModule) return ((com.vxv.chatbet.module.PickpocketingModule) activeModule).getSuccessesSinceLastEtc().get(); return attemptsSinceLastEtc.get(); }
public long getSuccessesSinceLastEtc() { if (activeModule instanceof com.vxv.chatbet.module.PickpocketingModule) return ((com.vxv.chatbet.module.PickpocketingModule) activeModule).getSuccessesSinceLastEtc().get(); return successesSinceLastEtc.get(); }