package io.github.freshsupasulley.taboo_trickler;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

public enum TricklerCategory {
	
	GOOD(1, "Good"), BAD(29, "Bad"), VERY_BAD(50, "Very Bad"), CRUSHING(20, "Crushing");
	
	private final String fancyName;
	private final int chance;
	
	public final List<SidedPunishment<?>> punishments = new ArrayList<>();
	
	TricklerCategory(int chance, String fancyName)
	{
		this.chance = chance;
		this.fancyName = fancyName;
	}
	
	public String getFancyName()
	{
		return fancyName;
	}
	
	public int getChance()
	{
		return chance;
	}
	
	public static TricklerCategory random(TricklerCategory... exclude)
	{
		var excluded = Set.of(exclude);
		
		// Take out all excluded enums
		var pool = Arrays.stream(values()).filter(category -> !excluded.contains(category)).toList();
		
		if(pool.isEmpty())
			throw new IllegalStateException("Tried to select a random category but excluded everything");
		
		int total = pool.stream().mapToInt(c -> c.chance).sum();
		int pick = (int) (Math.random() * total);
		int cumulative = 0;
		
		for(var cat : pool)
		{
			cumulative += cat.chance;
			if(pick < cumulative)
				return cat;
		}
		
		// This should never happen
		throw new IllegalStateException("Failed to select a TricklerCategory from the pool");
	}
}
