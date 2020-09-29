Zipkin Core Library Rationale
==============

Much of this rationale is the same as [Brave](https://github.com/openzipkin/brave/blob/master/brave/RATIONALE.md), for consistency reasons. Some
aspects, such as Java language level, directly support older versions of Brave.
However, looking only at Brave will not give a full impact of choices here.
This library is used for streaming pipelines and other instrumentation
libraries. It is important to consider carefully before revisiting topics here.

While many ideas are our own, there are notable aspects borrowed or adapted
from others. It is our goal to cite when we learned something through a prior
library, as that allows people to research any rationale that predates our
usage.

Below is always incomplete, and always improvable. We don't document every
thought as it would betray productivity and make this document unreadable.
Rationale here should be limited to impactful designs, and aspects non-obvious,
non-conventional or subtle.

## Java conventions
We only expose types public internally or after significant demand. This keeps
the api small and easier to manage when charting migration paths. Otherwise,
types are always package private.

Methods should only be marked public when they are intentional apis or
inheritance requires it. This practice prevents accidental dependence on
utilities.

### Why no private symbols? (methods and fields)
Zipkin is a library with embedded use cases, such as inside Java agents or
Android code.

For example, Android has a [hard limit on total methods in an application](https://developer.android.com/studio/build/multidex#avoid).
Fields marked private imply accessors in order to share state in the same
package. We routinely share state, such as codec internals within a package.
If we marked fields private, we'd count against that limit without adding
value.

Modifiers on fields and methods are distracting to read and increase the size
of the bytecode generated during compilation. By recovering the size otherwise
spent on private modifiers, we not only avoid hitting limits, but we are also
able to add more code with the same jar size.

For example, Zipkin 2.21 remains less than 250KiB, with no dependencies,
including an in-memory storage implementation and embedded JSON, ProtoBuf and
Thrift codecs.

This means we do not support sharing our packages with third parties, but we
do support an "always share inside a package" in our repository. In other
words, we trust our developers to proceed with caution. In the first seven
years of project history, we have had no issues raised with this policy.

### Java 1.6
Some dependents of this library are instrumentation in nature and are limited
to a floor JRE version. Java 1.6 is still a traced technology of August 2020.
Here's a quote from Fabian Lange of Instana, a tracing vendor, at that time:

> We have 1.6 / 1.7 customers and we happily support them with our auto instrumentation.

While other modules in this project are higher Java levels, the core library
remains at 1.6. This allows the highest applicability at the cost of
inconvenience to core library maintainers.

#### Why is the compiler plugin set to source level 1.7?
JDK 11 was the last JDK to allow writing Java 1.6 bytecode. We set the default
compile target to 1.7 in order to allow passing users to use any recent JDK.

We enforce 1.6 only on the `release` Maven profile. This also requires a JDK no
later than 11. We will have a decision to make when JDK 11 goes out of support
in September 2023. Choices could include switching to a more flexibly supported
JDK such as Zulu for the release process.

The disparity between 1.7 and 1.6 can cause some problems in maintenance. For
example, the IDE may suggest switching to diamond syntax, which will break
compilation in 1.6. This is why the `release` profile is used in at least one
CI matrix. The poor experience is limited to a rarely modified and smaller part
of the project (this library).


Note, an alternative is to use [Retrolambda](https://github.com/luontola/retrolambda) to backport 1.7 features to 1.6
bytecode. However, this [stopped working in JDK 15](https://github.com/luontola/retrolambda/issues/161), so we can't use this tactic
until that's resolved or another tool surfaces to do the same.

#### Why are tests in a different module?
The more common IDE in use in IntelliJ, which does yet support mixing source
levels between main and test trees in [Maven projects](https://youtrack.jetbrains.com/issue/IDEA-85478).

Until the above changes, we moved all unit tests to the zipkin-tests project.
This allows them to use Java 1.8 language features. Once IntelliJ changes, we
can likely move those tests back with little impact.

### Zero dependency policy
Some dependents of this library are instrumentation in nature, and we cannot
predict what 3rd party dependencies they will have. Attempting to do that would
limit the applicability of this library, which is an anti-goal. Instead, we
choose to use nothing except floor Java version features, currently Java 6.

Here's an example of when things that seem right aren't. We once dropped our
internal `@Nullable` annotation (which is source retention), in favor of JSR
305 (which is runtime retention). In doing so, we got `null` analysis from
Intellij. However, we entered a swamp of dependency conflicts, which started
with OSGi (making us switch to a service mix bundle for the annotations), and
later Java 9 (conflict with apps using jax-ws). In summary, it is easiest for
us to have no dependencies also, as we don't inherit tug-of-war between modular
frameworks who each have a different "right" answer for even annotations!

Incidentally, IntelliJ can be configured to use `zipkin2.internal.Nullable`, now.
Search for `Nullable` under inspections to configure this.

### Why `new NullPointerException("xxx == null")`
For public entry points, we eagerly check null and throw `NullPointerException`
with a message like "xxx == null". This is not a normal pre-condition, such as
argument validation, which you'd throw `IllegalArgumentException` for. What's
happening here is we are making debugging (literally NPEs are bugs) easier, by
not deferring to Java to raise the NPE. If we deferred, it could be confusing
which local was null, especially as deferring results in an exception with no
message.

