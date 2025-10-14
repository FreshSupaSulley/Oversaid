package io.github.freshsupasulley.taboo_trickler;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

public enum OversaidCategory {
	
	GOOD(1, "Good"), BAD(40, "Bad"), VERY_BAD(20, "Very Bad"), CRUSHING(5, "Crushing");
	
	private final String fancyName;
	private final int weight;
	
	public final List<SidedPunishment<?>> punishments = new ArrayList<>();
	
	OversaidCategory(int chance, String fancyName)
	{
		this.weight = chance;
		this.fancyName = fancyName;
	}
	
	public String getFancyName()
	{
		return fancyName;
	}
	
	public static OversaidCategory random(OversaidCategory... exclude)
	{
		var excluded = Set.of(exclude);
		
		// Take out all excluded enums
		var pool = Arrays.stream(values()).filter(category -> !excluded.contains(category)).toList();
		
		if(pool.isEmpty())
			throw new IllegalStateException("Tried to select a random category but excluded everything");
		
		int total = pool.stream().mapToInt(c -> c.weight).sum();
		int pick = (int) (Math.random() * total);
		int cumulative = 0;
		
		for(var cat : pool)
		{
			cumulative += cat.weight;
			if(pick < cumulative)
				return cat;
		}
		
		// This should never happen
		throw new IllegalStateException("Failed to select a TricklerCategory from the pool");
	}
}
