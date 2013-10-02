package com.m00ware.ftpindex;

import java.util.List;

/**
 * @author Wooden
 * 
 */
public interface EmbeddedObjectsDB {
    public <T> List<T> getEmbeddedObjects(Class<T> clazz);

    public boolean saveEmbeddableObject(Object obj);

    public boolean saveEmbeddableObjects(List<?> obj);

    public boolean removeEmbeddableObject(Object obj);

    public boolean removeEmbeddableObjects(List<?> obj);

    public <T> T createEmbeddableObject(Class<T> clazz);
}