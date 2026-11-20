# Graph Tools

Execute graph grammar productions, inspect the resulting graph, and export
its adjacency matrix or the complete execution history to an offline HTML report.

**Example application code is in [Main.java](src/main/java/com/github/Main.java),
in the lab01() and lab02() methods.** Code examples are maintained there rather
than duplicated in this documentation.

## Requirements and running the application

- JDK 26 or a compatible newer JDK.
- Maven, with access to dependencies on the first build.
- A browser with JavaScript enabled to view reports.

The simplest way to run the application is to import pom.xml into an IDE,
enable annotation processing for Lombok, and run com.github.Main.

From a Linux or macOS terminal, run the following commands from the project root:

1. Build and resolve the runtime classpath with
   `mvn -Dmaven.compiler.proc=full compile dependency:build-classpath -Dmdep.outputFile=target/classpath.txt`.
2. Start the selected example with
   `java -cp "target/classes:$(cat target/classpath.txt)" com.github.Main`.
3. Open the output HTML file printed to the console in a browser.

Rebuild after changing Java code or resource files. No web server is needed.

## Choosing an example in Main

Main.java contains example application code, not a command-line interface.
Main.main() currently calls lab01(). To run the history example, replace that
call with lab02(). Command-line arguments are not parsed.

| Method | Input used for execution | Ordered production sequence | Report |
| --- | --- | --- | --- |
| lab01() | startGraph from examples/grammar01.json | P1, P1, P3, P2, P2 | target/graph.html |
| lab02() | examples/graph2-01.json and examples/grammar2-01.json | P1, P4, P6, P7 | target/graph-history.html |

lab01() also reads additional graph resources as example data. Those graphs
are not passed to executeProductions(); that method uses the grammar's startGraph.

## Configuring your own execution

1. Place your grammar and, if needed, an external graph under src/main/resources.
2. Select the relevant example method in Main.main().
3. Change the resource paths in that method.
4. Set the ordered production names passed to executeProductions() or apply().
5. For TransformationSystem, choose match selection, dangling-edge handling
   and, optionally, the execution mode using the parameters below.
6. Set the output Path passed to HtmlHistoryRenderer.write(), rebuild and run.

| Setting | Location | Meaning |
| --- | --- | --- |
| Example to run | Main.main() | Calls lab01() or lab02() |
| Grammar resource | FileContentReader.read() in Main | Classpath path, such as /examples/grammar01.json |
| External initial graph | Graph.fromJson() in lab02() | Graph used instead of grammar.startGraph |
| Grammar initial graph | startGraph in grammar JSON | Used by Grammar.executeProductions() and grammar-only executor constructors |
| Production sequence | First argument of executeProductions() or apply() | Ordered list of existing production names; names may repeat |
| Match selection | Second argument of TransformationSystem.apply() | All non-overlapping matches or one match per requested production |
| Dangling-edge policy | Third argument of apply(), or danglingEdges in JSON | Reject or delete unembedded context edges |
| Execution mode | Fourth argument of the string-based apply() overload | Mapping and embedding precedence |
| Output path | Last argument of HtmlHistoryRenderer.write() | Relative to the working directory, or absolute |

FileContentReader reads classpath resources, not arbitrary filesystem paths.
The resource path /examples/custom.json refers to
src/main/resources/examples/custom.json. See Main.java for complete loading,
execution and export examples, including the required imports.

## Execution parameters and defaults

The following parameters belong to TransformationSystem.apply(). They are
Java API arguments, not command-line flags.

| Parameter | Accepted values | Default or behavior |
| --- | --- | --- |
| productions | Ordered List<String> | Names must exist in grammar.productions |
| selectionStrategy, int | 1, 2 | 1 = all eligible non-overlapping matches; 2 = one match |
| selectionStrategy, String | all, 1, single, 2, null | null = all |
| danglingEdges, Integer | 1, 2, null | 1 = forbid; 2 = delete; null = use JSON settings |
| danglingEdges, String | forbid, 1, delete, 2, null | null = use JSON settings |
| executionMode | EMBEDDING_PRIORITY, LEGACY | Default EMBEDDING_PRIORITY; null is invalid |

The one-argument apply() method selects all matches, uses the JSON policy and
the default execution mode. The two-argument overload additionally selects the
match strategy. The three-argument overload additionally selects the policy.
The four-argument overload uses String selection/policy arguments and adds
ExecutionMode. The String argument values are case-sensitive.

An explicit policy argument overrides production.danglingEdges, which overrides
grammar.danglingEdges. The grammar policy defaults to forbid.

EMBEDDING_PRIORITY disables a mapping when an embedding names the same left-side
vertex. That vertex is replaced and embedding rules handle its context edges.
Mappings without such embeddings preserve the matched host vertex ID.
LEGACY preserves mappings and ignores embeddings for mapped left-side vertices.
Embeddings for unmapped left-side vertices still apply.

All-match execution considers non-overlapping matches for that step; it does
not repeatedly apply a production until no matches remain. A production with
no match advances the requested sequence without changing the graph.

## What each execution parameter changes

### productions: which rules run, and in what order

Location: the first argument of TransformationSystem.apply() or
Grammar.executeProductions(), and the production list passed to GrammarExecutor.

This is an ordered sequence of production names from the grammar JSON.
Each entry requests one execution step. Repeating a name requests another
step using the graph produced by the preceding steps.

For example, requesting P1 twice means two consecutive P1 steps, not two
matches selected at once. The number of matches within each step is controlled
separately by selectionStrategy.

An unknown name rejects execution. A valid production with no matching left
side leaves the graph unchanged and advances to the next requested name.

### selectionStrategy: how many matches one step can replace

Location: the second argument of TransformationSystem.apply().

A match is an occurrence of the production's left-side graph in the current
graph. One production can have several possible matches.

- single, or numeric value 2: replace at most one matching occurrence.
  Other occurrences remain for later steps.
- all, or numeric value 1: consider all candidate occurrences for this step
  and replace eligible occurrences that do not share vertices.

If two candidates share a vertex, both cannot be selected in the same step.
Candidates are processed in a defined order, so all does not guarantee the
largest possible set of replacements.

All does not keep searching indefinitely. Matches created by the replacements
are not automatically processed again in the same step. Request the production
again in productions to perform another step.

Default: all in TransformationSystem.apply(). In contrast,
Grammar.executeProductions() and GrammarExecutor.next() select one match
per requested production. GrammarExecutor.next(boolean many) exposes the
same choice through many: true means all, false means single.

### danglingEdges: what happens to context edges left without an endpoint

Location: the third argument of TransformationSystem.apply(), the optional
String policy argument of GrammarExecutor, or danglingEdges in grammar JSON
and individual production objects.

A context edge is an existing connection that is not explicitly removed
as part of the matched left-side pattern. If its vertex is replaced,
an embedding can reconnect it to a right-side vertex.

This parameter decides what to do with connections still attached to a
vertex scheduled for deletion after embedding rules have been processed:

- forbid, or numeric value 1: reject the current step if any such edge remains.
  This helps detect an incomplete embedding specification.
- delete, or numeric value 2: remove those remaining connections and allow
  the replacement to continue.

This policy does not decide whether a mapped vertex is preserved and does
not disable embeddings. It handles connections left over after those rules.

A null execution argument means inherit the policy, not delete edges.
The first available setting wins: execution argument, production setting,
then grammar setting. The grammar default is forbid.

An explicit execution argument applies to every production in that call,
even if an individual production specifies another policy.

### executionMode: whether an embedding takes precedence over a mapping

Location: the fourth argument of the String-based TransformationSystem.apply()
overload, or the fifth argument of the full GrammarExecutor constructor.
It is not currently a JSON field or a command-line flag.

A mapping identifies a left-side vertex that should survive as a right-side
vertex, keeping its host graph ID. An embedding specifies how to reconnect
existing connections when a left-side vertex is replaced.

- EMBEDDING_PRIORITY: if an embedding names the same left-side vertex as a
  mapping, ignore that mapping. Replace the vertex and reconnect its context
  edges using embeddings. The new vertex receives a new positive ID.
  Mappings for other vertices remain effective.
- LEGACY: preserve the mapping and ignore embeddings for that same left-side
  vertex. Its host ID and context connections are retained. Embeddings for
  other, unmapped vertices still apply.

If a mapped vertex has no embedding, it retains its ID in either mode.
An embedding for one vertex does not disable mappings for unrelated vertices.

Default: EMBEDDING_PRIORITY. Omitting the mode selects that default;
explicitly passing null is invalid.

### How the options interact

These settings answer separate questions:

| Question | Parameter |
| --- | --- |
| Which production is requested next? | productions |
| How many existing occurrences can this step replace? | selectionStrategy |
| Does a mapping survive a conflicting embedding? | executionMode |
| What happens to connections not handled by embeddings? | danglingEdges |

Choosing EMBEDDING_PRIORITY does not imply delete. With forbid, a replacement
still fails if its embedding rules leave context edges unhandled.

In Main.lab02(), the two numeric arguments after the production list are
currently 1 and 1. The first selects all non-overlapping matches; the second
forbids deleting unembedded context edges. The omitted execution mode defaults
to EMBEDDING_PRIORITY.

## Choosing the execution API

| API | Use it when | Behavior |
| --- | --- | --- |
| Grammar.executeProductions() | Only the final graph is needed | Starts from a copy of startGraph; at most one match per listed production; no History object |
| TransformationSystem.apply() | The input is an external graph or history is needed | Updates the supplied Graph and appends snapshots |
| GrammarExecutor.next() / next(boolean many) | Execution must be controlled step by step | Works on an internal copy; false selects one match, true selects all eligible matches |

GrammarExecutor.hasNext() checks whether requested production names remain.
Its getGraph() returns a copy. TransformationSystem.getGraph() returns the
updated graph. Save a copy of the input before apply() if it will be included
in a history report; lab02() demonstrates this.

## Reading the reports

Open target/graph.html for lab01(), or target/graph-history.html for lab02().
Each report is overwritten on the next export to the same path.

- The single-graph report contains the resulting graph and its matrix.
- The history report starts with the input graph; its last section is the result.
- Use the top navigation to jump between snapshots.
- Use +, − and Reset to change the graph view.
- Scroll matrices horizontally and vertically to inspect all cells.
- Matrix headers contain vertex labels and IDs, ordered by index then label.
- Cells show all edge weights and labels for the pair; — means no edge.
- Rows represent sources and columns represent targets in directed graphs.
- Expand Applied production to inspect its left and right sides, mappings
  and embeddings as defined in the source grammar.

lab02() also prints the resulting graph and history to the console.

## Common problems

| Symptom | Check |
| --- | --- |
| Missing getters or constructors during compilation | Enable Lombok annotation processing; use the documented Maven command |
| Resource cannot be read | Place it under src/main/resources and use a classpath path |
| Production does not exist | Match production names exactly, including case |
| Graph does not change | The requested left-side pattern may have no match |
| Unembedded context edges reject a step | Add an appropriate embedding or deliberately choose delete |
| Old report is displayed | Rerun the selected example, refresh the browser and check the printed output path |

See [technical documentation](docs/TECHNICAL.md) for data contracts, API details
and the transformation algorithm.
