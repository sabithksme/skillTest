package com.sks.skill.puzzles;

public abstract class Sink<T> {
	
	abstract void add(T... elements);
	
	void addUnlessNull(T...elements){
		for( T element : elements){
			if(element!=null){
				add(element);
			}
		}
	}
}
