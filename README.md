# scalaz-par

This code is a response to a
[blog post exploring FP APIs for parallel effects](http://scalamusings.blogspot.com/2015/11/parallelizing-independent-effectual-sub.html).

What we're trying to do is make a `Par` data type that has an `Applicative`
instance that does parallel computation.  This is complicated by the fact that
on the JVM we need a thread pool for concurrency.

We also want to have a `Serial` data type that is isomorphic to `Par` but has
the normal `Monad` instance we have with `Task` and `IO` (that sequences
effects).

I'd like to get some feedback on this approach.  Please find me on
 #scalaz/Freenode as "tnks" if you have any feedback.
