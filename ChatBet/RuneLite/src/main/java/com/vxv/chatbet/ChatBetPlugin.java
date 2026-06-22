@Subscribe
public void onStatChanged(StatChanged event) {
    if (event.getSkill() == Skill.THIEVING) {
        lastThievingXp = event.getXp();
        if (activeModule != null) {
            activeModule.onStatChanged(event);  // delegate for future module use
        }
    }
}

@Subscribe
public void onGameTick(GameTick event) {
    if (activeModule != null) {
        activeModule.onGameTick(event);
    }

    // More persistent XP seeding
    if (lastThievingXp <= 0 && client != null) {
        int xp = client.getSkillExperience(Skill.THIEVING);
        if (xp > 0) {
            lastThievingXp = xp;
        }
    }
}

public int getXpToGoal() {
    int goal = config.thievingGoalXp();
    int targetMark = (int) (goal * (currentGoalPercentage / 100.0));

    if (client != null) {
        int current = client.getSkillExperience(Skill.THIEVING);
        if (current > 0) {
            lastThievingXp = current;
            return Math.max(0, targetMark - current);
        }
    }

    // Fallback to last known value
    if (lastThievingXp > 0) {
        return Math.max(0, targetMark - lastThievingXp);
    }

    return Math.max(0, targetMark);
}