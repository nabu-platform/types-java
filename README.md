# Java Types

This package builds on the types-base package and adds full support for java beans through the API defined in types-api.
It uses reflection to access the beans and supports a few existing specs like:

- JAXB: a number of JAXB annotations can be reused to determine field names when binding to XML, JSON,... Things like propOrder are also supported.
- Java Bean Validation: it supports a number of the validation annotations (http://beanvalidation.org/1.1/spec/#builtinconstraints):

```
Null			X
NotNull			V
AssertTrue		X
AssertFalse		X
Min				V
Max				V
DecimalMin		V
DecimalMax		V
Size			V
Digits			X (bigdecimal, biginteger, string, byte, short, int, long, NOT double etc)
Past			~ (only java.util.Date, not calendar)
Future			~ (only java.util.Date, not calendar)
Pattern			V
```

It also adds bean resolving to the defined type resolving.

## OSGi

This library works without a hitch on a regular JVM with SPI. OSGi is a different story alltogether though.
The problem is that the library needs to dynamically find classes which is of course rather hard.

Suppose this is your hierarchy

- types-api
	- types-java
		- myTestPackage
			> MyTest.java

If you ask BeanResolver to resolve the class "MyTest", it will not be able to actually see it in OSGi because the library is limited to its declared OSGi dependencies.
An interesting description of the problem can be found here: http://njbartlett.name/2010/08/30/osgi-readiness-loading-classes.html

To make it work in OSGi, there are a couple of options:

### Register Classes

You can register any class like this: 

```java
BeanResolver.getInstance().register(MyTest.class)
```

This is generally only an interesting approach if you have a very small amount of classes to expose.

### Domain Object Factory

You can create a DomainObjectFactory implementation and use OSGi to add it, for example:

```java
package test;

import be.nabu.libs.types.java.DomainObjectFactory;

public class MyObjectFactory implements DomainObjectFactory {
	@Override
	public Class<?> loadClass(String name) throws ClassNotFoundException {
		return getClass().getClassLoader().loadClass(name);
	}
}
```

In the pom of your project you put:

```xml
<plugin>
	<groupId>org.apache.felix</groupId>
	<artifactId>maven-bundle-plugin</artifactId>
	<version>2.3.7</version>
	<extensions>true</extensions>
	<configuration>
		<instructions>
			<Bundle-SymbolicName>${project.groupId}.${project.artifactId}</Bundle-SymbolicName>
			<Bundle-Name>${project.artifactId}</Bundle-Name>
			<Export-Package>${project.groupId}</Export-Package>
			<Bundle-Version>1.0.0</Bundle-Version>
			<Service-Component>OSGI-INF/*.xml</Service-Component>
		</instructions>
	</configuration>
</plugin>
```

And in the folder `src/main/resources/OSGI-INF` create a file called MyDomainObjectFactory.xml and put in it:

```xml
<scr:component xmlns:scr="http://www.osgi.org/xmlns/scr/v1.1.0" name="test.MyObjectFactory">
	<implementation class="test.MyObjectFactory" />
	<service>
		<provide interface="be.nabu.libs.types.java.DomainObjectFactory"/>
	</service>
</scr:component>
```

This will automatically add the domain factory to the beanresolver instance.
This is the most complex but by far the cleanest solution. If you have a sufficiently large application, this is the advisable way to do it.

### ClassLoader Magic

If the bean resolver can not find a domain factory or a registered class, it will do a lookup using the context classloader of the thread.
If the context classloader is correctly set, it will work. In general this is not an advisable solution.

### OSGi Magic

In the pom of this project you will find the following line:

```xml
<DynamicImport-Package>*</DynamicImport-Package>
```

The exact explanation of this setting can be found here: http://wiki.osgi.org/wiki/DynamicImport-Package.
For sufficiently small applications, this is by far the easiest option and it is in fact the default.