package be.nabu.libs.types.java;

public interface DomainObjectFactory {
	public Class<?> loadClass(String name) throws ClassNotFoundException;
}
