package io.github.freshsupasulley.oversaid;

import io.github.freshsupasulley.oversaid.forge.Config;
import io.github.freshsupasulley.oversaid.forge.Oversaid;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.IntStream;

public enum OversaidCategory {
	
	GOOD(0, "Good"), BAD(80, "Bad"), VERY_BAD(19, "Very Bad"), CRUSHING(1, "Crushing");
	
	private final int weight;
	private final String fancyName;
	
	OversaidCategory(int weight, String fancyName)
	{
		this.weight = weight;
		this.fancyName = fancyName;
	}
	
	public List<Integer> getPunishments()
	{
		List<Integer> result = new ArrayList<>();
		
		for(int i = 0; i < Oversaid.punishments.size(); i++)
		{
			var sample = Oversaid.punishments.get(i);
			
			if(sample.getCategory() == this)
			{
				result.add(i);
			}
		}
		
		return result;
	}
	
	public int getWeight()
	{
		return weight;
	}
	
	public String getFancyName()
	{
		return fancyName;
	}
	
	public static OversaidCategory random()
	{
		// Take out all excluded enums
		var configWeights = Config.WEIGHTS.get();
		var weights = new int[values().length];
		
		for(int i = 0; i < weights.length; i++)
		{
			weights[i] = i >= configWeights.size() ? OversaidCategory.values()[i].weight : configWeights.get(i);
		}
		
		int total = IntStream.of(weights).sum();
		int pick = (int) (Math.random() * total);
		int cumulative = 0;
		
		for(int i = 0; i < OversaidCategory.values().length; i++)
		{
			cumulative += weights[i];
			
			if(pick < cumulative)
				return OversaidCategory.values()[i];
		}
		
		// This should never happen
		throw new IllegalStateException("Failed to select a TricklerCategory from the pool");
	}
}
