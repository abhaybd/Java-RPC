package com.coolioasjulio.rpc.exclusionstrategies;

import com.google.gson.ExclusionStrategy;
import com.google.gson.FieldAttributes;

import java.lang.reflect.Field;

public class SuperclassExclusionStrategy implements ExclusionStrategy {
    @Override
    public boolean shouldSkipClass(Class<?> arg0) {
        return false;
    }

    @Override
    public boolean shouldSkipField(FieldAttributes fieldAttributes) {
        String fieldName = fieldAttributes.getName();
        Class<?> theClass = fieldAttributes.getDeclaringClass();

        return isFieldInSuperclass(theClass, fieldName);
    }

    private boolean isFieldInSuperclass(Class<?> subclass, String fieldName) {
        Class<?> superclass = subclass.getSuperclass();
        Field field;

        while (superclass != null) {
            field = getField(superclass, fieldName);

            if (field != null)
                return true;

            superclass = superclass.getSuperclass();
        }

        return false;
    }

    private Field getField(Class<?> theClass, String fieldName) {
        try {
            return theClass.getDeclaredField(fieldName);
        } catch (Exception e) {
            return null;
        }
    }
}
