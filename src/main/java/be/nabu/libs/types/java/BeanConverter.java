/*
* Copyright (C) 2014 Alexander Verbruggen
*
* This program is free software: you can redistribute it and/or modify
* it under the terms of the GNU Lesser General Public License as published by
* the Free Software Foundation, either version 3 of the License, or
* (at your option) any later version.
*
* This program is distributed in the hope that it will be useful,
* but WITHOUT ANY WARRANTY; without even the implied warranty of
* MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
* GNU Lesser General Public License for more details.
*
* You should have received a copy of the GNU Lesser General Public License
* along with this program. If not, see <https://www.gnu.org/licenses/>.
*/

package be.nabu.libs.types.java;

import be.nabu.libs.types.api.TypeConverter;
import be.nabu.libs.types.api.TypeInstance;

public class BeanConverter implements TypeConverter {

	@SuppressWarnings({ "rawtypes", "unchecked" })
	@Override
	public <T> T convert(Object instance, TypeInstance from, TypeInstance to) {
		if (instance == null || !(to.getType() instanceof BeanType)) {
			return null;
		}
		Object original;
		if (instance instanceof BeanInstance) {
			original = ((BeanInstance) instance).getUnwrapped();
		}
		else {
			original = instance;
		}
		if (((BeanType) to.getType()).getBeanClass().isAssignableFrom(original.getClass())) {
			return (T) new BeanInstance((BeanType) to.getType(), original);
		}
		return null;
	}

	@SuppressWarnings({ "unchecked", "rawtypes" })
	@Override
	public boolean canConvert(TypeInstance from, TypeInstance to) {
		return from.getType() instanceof BeanType && to.getType() instanceof BeanType
				&& ((BeanType) to.getType()).getBeanClass().isAssignableFrom(((BeanType) from.getType()).getBeanClass());
	}

}
