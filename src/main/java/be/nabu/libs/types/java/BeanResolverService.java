package be.nabu.libs.types.java;

import be.nabu.libs.types.api.DefinedType;
import be.nabu.libs.types.api.DefinedTypeResolver;

public class BeanResolverService implements DefinedTypeResolver {

	@Override
	public DefinedType resolve(String id) {
		return BeanResolver.getInstance().resolve(id);
	}

}
