package com.coolioasjulio.rpc.exclusionstrategies;

import com.google.gson.ExclusionStrategy;
import com.google.gson.FieldAttributes;

import java.util.Arrays;
import java.util.Set;

public class WhitelistExclusionStrategy implements ExclusionStrategy
{
    private Set<Class<?>> includeSet;
    public WhitelistExclusionStrategy(Set<Class<?>> includeSet)
    {
        this.includeSet = includeSet;
    }

    @Override
    public boolean shouldSkipField(FieldAttributes fieldAttributes)
    {
        Class<?> declaredClass = fieldAttributes.getDeclaredClass();
        Class<?> declaringClass = fieldAttributes.getDeclaringClass();
        if(declaredClass.isPrimitive() || declaredClass.isArray() || includeSet.contains(declaringClass))
            return false;
        return !Arrays.stream(declaredClass.getFields())
            .map(e -> includeSet.contains(e.getType()))
            .reduce(Boolean::logicalOr)
            .orElse(false);
    }

    @Override
    public boolean shouldSkipClass(Class<?> clazz)
    {
        return false;
    }
}
