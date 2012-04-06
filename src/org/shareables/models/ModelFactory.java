package org.shareables.models;

/**
 * @author Niklas Schnelle
 *
 * creates model instances
 */
public interface ModelFactory {
    public ShareableModel createModel();
    public String getName();
}
