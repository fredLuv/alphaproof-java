# Your Java Code Passes the Tests. Can You Prove It’s Extensible?

Inspired by Google DeepMind’s AlphaProof, I asked Gemini to put together a small, standalone Java demo to show me how a program could enforce specific OOP design rules.

Consider two Java implementations that both return `6.0` for the area of a `3–4–5` triangle. One uses the Visitor pattern. The other branches on `if (shape instanceof Triangle)`. The same area test passes for both.

Now a teammate wants to calculate perimeter without editing any of the shape classes. That’s exactly the kind of change the Visitor pattern is designed to support. Could we check that promise in CI? The **OOPProof-Java** demo explores that question by trying the extension and recording evidence that a separate checker can inspect.

I started with two familiar designs, a shape visitor and a Rock-Paper-Scissors engine. For each, I wrote down what the design promised and what evidence would justify believing it.

## 1. Start with a clean build

The first layer checks that the source tree is free of stray `.class` and `.jar` files, then compiles the project with `javac -Xlint:all`. Warnings and errors both fail the check.

That gives the later checks a clear starting point: the source under review compiles cleanly.

## 2. Ask the compiler how the code works

For the Visitor example, I wanted to verify something an area assertion can’t tell me: who decides which method runs?

The checker reads the compiler’s abstract syntax tree and inspects the dispatch path. In `Circle.accept(visitor)`, it looks for the call to `visitor.visitCircle(this)`. It also checks the inspected source for `instanceof` and `getClass()` shortcuts and verifies that the fields covered by the spec are `private`.

If someone rewrites the visitor as a chain of type checks, the area can still be correct. The structural check can point to the design rule that failed.

## 3. Match each claim to a check

For geometry, the spec exercises properties across selected inputs. A circle with radius `0` or `NaN` must throw `IllegalArgumentException`. So must `Triangle(1, 2, 10)`. Doubling every side should multiply the area by four, within floating-point tolerance. A thin triangle with sides `(100000, 100000, 1)` exercises the area calculation, where `AreaVisitor` uses Kahan’s rearrangement of Heron’s formula.

Rock-Paper-Scissors gives us a stronger result because its input space is finite. Three moves produce exactly nine ordered matchups. The checker runs all nine and verifies that every pair has an outcome, each move draws against itself, and reversing the players reverses every win or loss.

The scope matters. Nine matchups cover the entire three-move game. The geometry cases provide evidence about the inputs exercised. A universal claim about every valid triangle would require a different proof.

## 4. Try the extension a teammate would actually write

This is my favorite check. The engine generates client code, compiles it against the existing API, and runs it without editing the original implementation.

For shapes, it generates a `SyntheticPerimeterVisitor` that implements `ShapeVisitor<R>`, then runs it on circles, rectangles, and triangles. This checks one specific promise of the Visitor pattern: a teammate can add an operation across the existing shape types.

For the game, the promised extension is different: a teammate can add moves. The engine builds Rock-Paper-Scissors-Lizard-Spock through the public `Symbol` and `RuleSet.builder()` API, then checks all 25 ordered matchups. A hardcoded three-move implementation can pass every original matchup and still fail this check.

## What the checker caught

I ran **OOPProof-Java** against three deliberately flawed implementations. All three passed basic happy-path tests. The checker caught each one for a different reason:

- An `instanceof` shortcut returned the correct area but failed the Visitor dispatch check.
- A triangle constructor accepted `(1, 2, 10)` and failed the invalid-input check.
- A hardcoded Rock-Paper-Scissors implementation handled the original game but failed the five-move extension.

The repo also includes a declarative LRU cache spec. It runs 250-step randomized operation sequences against a custom doubly linked list implementation and a reference model across multiple capacities. That checks observable behavior over those sequences. Establishing an `O(1)` complexity bound is a separate question.

After a successful run, a small verification kernel checks the recorded steps and verifies the SHA-256 hashes of the Java source files. That ties the receipt to the exact source that was checked. To understand what the result covers, you can read the spec alongside it.

## Try it yourself

With JDK 21 or later, run:

```bash
git clone https://github.com/fredLuv/alphaproof-java.git OOPProof-Java
cd OOPProof-Java
./run.sh
```

[Explore OOPProof-Java on GitHub](https://github.com/fredLuv/alphaproof-java)

What’s one change your team’s architecture promises should be easy? Could you write the smallest client that makes that change and run it in CI?

#Java #SoftwareEngineering #DesignPatterns #FormalVerification
