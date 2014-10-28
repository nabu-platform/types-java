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