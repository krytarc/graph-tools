# Technical documentation

Example application code is maintained in
[Main.java](../src/main/java/com/github/Main.java), in lab01() and lab02().
This document describes the implementation and API contracts without duplicating
those examples. See [README](../README.md) for build and usage instructions.

## Architecture and ownership

All packages below are under com.github.graphtools.

| Package / class | Responsibility |
| --- | --- |
| model.GraphStructure | Mutable vertex/edge collections, graph validation, ID allocation and subgraph matching |
| model.Graph | GraphStructure with a name and graph-root JSON parser |
| model.Vertex / Edge | Vertex identity/label/coordinates and edge endpoints/weight/label |
| grammar.Grammar | Grammar configuration, validation and convenience execution delegation |
| grammar.Production | Left/right graphs, mappings, embeddings and optional policy override |
| execution.ExecutionMode | Derives effective mappings from the selected precedence rule |
| execution.GrammarExecutor | Owns the working graph, requested sequence, position and match-selection loop |
| execution.ProductionApplier | Mutates a working graph for an already matched production |
| execution.TransformationSystem | Updates the external Graph and appends history after each successful step |
| history.History / HistoryElement | Snapshot collection; HistoryElement copies the resulting Graph |
| io.FileContentReader | UTF-8 classpath resource reading |
| visualization.HtmlHistoryRenderer | Single-graph/history data serialization and HTML file export |
| visualization.GraphVisualizer | Retained Swing visualizer; Main examples use HTML |

Main selects the example inputs and requested production sequence. Model is a
convenience container for a Graph and Grammar with JSON loading helpers.

## Public execution APIs

| Entry point | Initial graph | Mutation | History |
| --- | --- | --- | --- |
| Grammar.executeProductions(List<String>) | Copy of grammar.startGraph | Does not update startGraph | Not collected |
| GrammarExecutor(Grammar, List<String>) | Copy of grammar.startGraph | Internal executor state | Not collected |
| GrammarExecutor(GraphStructure, Grammar, List<String>) | Copy of supplied graph | Internal executor state | Not collected |
| TransformationSystem(Graph, Grammar), followed by apply() | Supplied Graph | Updates that graph after each successful step | Appends copied snapshots |

GrammarExecutor provides two additional policy constructors: one accepting
Grammar, List<String>, String; another accepting GraphStructure, Grammar,
List<String>, String. Its five-argument constructor adds ExecutionMode after
the String policy override. Other constructors use EMBEDDING_PRIORITY.

The policy override is forbid, delete or null. Unlike the string-based
TransformationSystem.apply() overloads, executor policy strings do not accept
numeric aliases. Policy validation accepts letter case variations.

| Method / parameter | Contract |
| --- | --- |
| GrammarExecutor.hasNext() | Whether unconsumed requested production names remain |
| GrammarExecutor.next() | Executes the next requested production with at most one match |
| GrammarExecutor.next(boolean many) | true applies all eligible non-overlapping matches; false applies one |
| GrammarExecutor.getGraph() | Returns a copy of the current executor graph |
| TransformationSystem.apply() | Overload/default details are listed in README; returns the same system |
| TransformationSystem.getGraph() | Returns its underlying mutable Graph |
| TransformationSystem.getHistory() | Returns the mutable History object |
| History.getHistory() | Exposes its mutable list; may be null before the first append |

The executor constructor copies the graph, validates its structure and
compatibility with grammar directedness, copies the requested names and checks
that every name exists. It does not call Grammar.validate(); parsing through
Grammar.fromJson() performs that validation. Programmatically assembled
grammars should be validated explicitly.

Grammar.executeProductions() delegates to GrammarExecutor. Its default is one
match per listed production. Grammar.applyProduction() remains a compatibility
delegation to ProductionApplier. These low-level application methods mutate the
supplied graph directly: they do not select matches, prepare an execution mode,
record history or provide their own transaction boundary.

## Parameter scopes and interpretation

| Parameter | Defined in | Scope |
| --- | --- | --- |
| productions | Java execution call / executor constructor | Ordered requested steps |
| selectionStrategy | TransformationSystem.apply() | Match count policy for every requested step in that call |
| many | GrammarExecutor.next(boolean) | Match count policy for the next requested step only |
| danglingEdges execution override | apply() / executor constructor | Overrides every production policy in that execution |
| production.danglingEdges | Production JSON object | Policy for that production when no execution override exists |
| grammar.danglingEdges | Grammar JSON object | Fallback policy |
| executionMode | apply() / full executor constructor | Effective mapping preparation for every requested production |
| startGraph | Grammar JSON object | Initial graph only when the selected API starts from the grammar |
| directed | Graph and grammar objects | Structural compatibility and edge orientation, not match selection |
| mapping.left / mapping.right | Production JSON object | Local indices in the left and right production graphs |
| embedding.leftVertex | Production JSON object | Local left-side index whose context connections are processed |
| embedding.neighborLabel | Production JSON object | Neighbor label filter; * accepts any label |
| embedding.connectTo | Production JSON object | Local right-side indices receiving the reconnected edges |

Production-local indices are translated through leftToGraph and rightToGraph.
They are not required to equal host graph vertex IDs.

An empty connectTo list explicitly removes context edges matching that
embedding. Edges consumed by this rule no longer count as unhandled dangling
edges. This is different from omitting an embedding, which leaves those edges
for the dangling-edge policy.

The presence of an embedding for a leftVertex triggers EMBEDDING_PRIORITY
even when its neighborLabel filter matches no current neighbor. In that case
the mapping is still disabled, and any remaining connections are handled by
forbid or delete.

Selection, precedence and dangling-edge policies are independent.
Match ordering determines which candidates are considered first; all-match
execution greedily skips overlaps and does not compute a maximum disjoint set.
One successful requested production produces one HistoryElement, regardless
of how many matches it replaced.

## JSON data contract

Graph.fromJson() requires a top-level graph object. Grammar.fromJson() requires
a top-level grammar object. GraphStructure.fromJson() requires graphStructure.
Nested startGraph, left and right graph objects have no root wrapper.
Existing complete input files are under src/main/resources/examples.

### Graph fields

| Field | Type | Purpose / validation |
| --- | --- | --- |
| graph.name | String | Optional descriptive name |
| directed | Boolean | Directionality; grammar default false; nested grammar graphs inherit it if omitted |
| vertices | Array<Vertex> | Required collection; indices must be unique within the graph |
| edges | Array<Edge> | Required collection; endpoints must reference existing vertices |
| vertex.index | int | Vertex identifier local to this graph |
| vertex.label | String | Grammar symbol; defaults to an empty string |
| vertex.x, vertex.y | Double | Optional coordinates; HTML uses a circular layout instead |
| edge.u, edge.v | int | Source and target indices in the same graph |
| edge.weight | Double | Defaults to 1.0; preserved when context edges are rewired |
| edge.label | String | Defaults to an empty string |

Input IDs may include zero or negative values. Only generated IDs are restricted
to positive values. Edges have no separate ID field. Vertices, edges and their
collections are mutable; graph copying duplicates vertices and edges.

### Grammar and production fields

| Field | Type | Purpose / default |
| --- | --- | --- |
| name | String | Descriptive grammar name |
| directed | boolean | Default false; nested graphs must have compatible directedness |
| danglingEdges | String | forbid by default; alternatively delete |
| labels.nonterminals | Array<String> | Unique nonterminal symbols |
| labels.terminals | Array<String> | Unique terminal symbols, disjoint from nonterminals |
| startGraph | Graph object | Required initial graph for convenience execution |
| productions | Array<Production> | Required; names must be unique and nonblank |
| production.name | String | Name used in the requested execution sequence |
| production.description | String | Optional description included in the report |
| production.danglingEdges | String | Optional override of grammar policy |
| production.left / right | Graph objects | Required pattern and replacement |
| production.mappings | Array<Mapping> | Default empty; JSON alias mapping |
| production.embeddings | Array<Embedding> | Default empty; JSON alias embedding |
| mapping.left / right | int | Existing indices in the respective production sides |
| embedding.leftVertex | int | Existing left-side index whose context is handled |
| embedding.neighborLabel | String | Neighbor symbol or * for any label |
| embedding.connectTo | Array<int> | Right-side target indices; empty removes matching context edges |

Use either the canonical field or its alias, not both. Mappings must be
one-to-one on each side. Every left-side graph must contain a nonterminal.
Vertex labels and nonempty edge labels in grammar graphs must belong to the
declared terminal/nonterminal symbol sets. Embeddings must reference existing
left-side vertices and existing right-side targets.

## Matching algorithm and ordering

GraphStructure.findSubGraphMapping(pattern, many) returns mappings from
left-side pattern indices to host graph vertex indices. Production.mappings
instead maps left-side indices to right-side indices. These maps have different
roles and must not be interchanged. Host IDs need not equal pattern IDs.

Matching uses JGraphT VF2SubgraphIsomorphismInspector. The internal graph adds
one structural vertex for each edge; its label participates in matching.
An additional marker represents an undirected loop. The comparator distinguishes
original vertices, edge vertices and loop markers by type, then compares labels.
Weights and x/y coordinates are not matching criteria.

Candidate mappings are compared lexicographically using their sorted host
indices, with left-side index ordering resolving ties. TransformationSystem
uses ascending host indices. Public GrammarExecutor.next() methods, including
those called by Grammar.executeProductions(), use descending ordering.
The ascending switch is package-private.

All-match execution enumerates candidates before replacement. A set of used
host IDs prevents overlapping matches. Each remaining candidate is revalidated
against the evolving working graph before application. Vertices created during
that step do not enter the original candidate list. This is not a repeat-until-
no-match algorithm.

## Production application

ExecutionMode prepares the effective production before match selection.
ProductionApplier then receives the working graph, prepared production,
leftToGraph mapping and boolean forbidDanglingEdges flag.

1. Allocate IDs for right-side vertices without effective mappings.
2. Build rightToGraph from right-side indices to host indices.
3. Resolve mapped right-side vertices through leftToGraph.
4. Remove edges explicitly matched by the left-side pattern.
5. Identify left-side vertices absent from the effective mappings.
6. Rewire their context edges using embedding rules.
7. Reject or delete remaining dangling context edges according to the policy.
8. Remove replaced left-side vertices.
9. Update right-side labels and add right-side edges.

New right-side vertices receive unique random IDs in 1..2147483647, even when
their production-local ID would be available in the host graph. Coordinates
are copied from the right side. Mapped vertices retain their host ID and
coordinates; their labels are updated. Rewired edges retain weight, label and
endpoint orientation.

Embedding rules are processed in list order. Each rule examines the graph as
modified by earlier rules, so overlapping rules can be order-sensitive.
The neighborLabel filter is checked against the neighboring vertex label.
A wildcard accepts any label, and multiple connectTo targets duplicate the
connection to the specified right-side vertices.

## Execution modes and dangling edges

EMBEDDING_PRIORITY collects leftVertex IDs referenced by embeddings and removes
only the mappings for those IDs from the effective production. The original
Production is unchanged. Those left-side vertices are then replaced, their
right-side counterparts receive new IDs, and embeddings reconnect context edges.
Mappings without overlapping embeddings continue to preserve host identity.

LEGACY keeps the source mappings. Mapped left-side vertices are not deleted,
so their embeddings are skipped by ProductionApplier. Their host IDs and
context edges are retained. Embeddings still process unmapped vertices.
Edges explicitly present on the left side are removed and right-side edges
are added in either mode.
The default mode is EMBEDDING_PRIORITY; a null mode is invalid.

Policy precedence is the explicit execution override, then production setting,
then grammar setting. forbid rejects remaining unembedded context edges; delete
removes them. EMBEDDING_PRIORITY does not implicitly change this policy.

## Transaction boundary and snapshots

Each GrammarExecutor.next() step modifies a copy and commits it only after all
selected matches succeed. A failure discards that step's candidate graph and
does not advance the sequence position. Earlier successful steps remain committed.

A requested production with no match advances the sequence unchanged.
TransformationSystem records a snapshot even in that case. It updates the
external Graph after each successful executor step, then appends a HistoryElement.
HistoryElement copies the Graph, isolating that snapshot from subsequent steps.
Repeated apply() calls append to existing history rather than clearing it.

## HTML export pipeline

HtmlHistoryRenderer.write() has two overloads. The two-argument overload accepts
GraphStructure and output Path and creates a single Result graph section.
The four-argument overload accepts input GraphStructure, Grammar, History and
output Path, prepends the input snapshot and resolves recorded production names
against grammar.productions. Unknown names reject the export.

The exporter serializes Step records containing title, graph and production.
It loads /visualization/history.html from the classpath and replaces the
__REPORT_DATA__ placeholder with JSON. HTML delimiters in the data are escaped
to prevent termination of the script element. The file is written as UTF-8;
parent directories are created and an existing output file is overwritten.
Relative output paths resolve against the process working directory.

The template contains its own CSS and JavaScript, with no CDN or server needed.
The browser inserts labels with textContent, draws circular SVG layouts and
builds adjacency tables indexed by endpoint pairs. Undirected pairs are indexed
in both directions, except loops. Parallel edges remain separate cell entries.
Rows are sources, columns are targets, and headers sort by index then label.

The report includes graph zoom controls, scrollable matrices and expandable
production panels. Production panels show the source rules, not individual
match traces or effective mappings after mode preparation. The single-graph
and history reports use the same renderer and styles.

## Errors and implementation limits

| Condition | Behavior |
| --- | --- |
| Malformed graph/grammar JSON or invalid references | IllegalArgumentException |
| Unknown requested production name | IllegalArgumentException before executor steps begin |
| Unsupported selection strategy or policy | IllegalArgumentException |
| Null execution mode | NullPointerException |
| No matching left side | Sequence advances without a graph change |
| Unembedded context edges under forbid | IllegalStateException; current executor step is not committed |
| next() after sequence exhaustion | NoSuchElementException |
| Missing HTML template or unwritable output | IOException |

Mutable model objects are not designed for concurrent execution against shared
instances. Random IDs make output IDs nondeterministic across runs. Snapshots
are held in memory and embedded in one report, so large histories increase both
memory use and HTML size. Matching repeatedly enumerates subgraph mappings;
large host graphs or highly repetitive labels may be expensive.

The renderer supports directed loops, but directed matching conversion has no
separate loop marker; validate loop-matching behavior for the required grammar
before relying on it. GraphStructure validation checks structure, while grammar
validation additionally checks symbol sets and production references.

## Build and resources

The Maven compiler target is Java 26. Lombok generates getters, setters and
constructors; annotation processing must be enabled. The README build command
uses -Dmaven.compiler.proc=full for this purpose.

Generated classes, runtime classpath and HTML reports are under target/, which
is ignored by Git. FileContentReader reads UTF-8 classpath resources and does
not open arbitrary filesystem paths. Input JSON resources are not rewritten
by execution or export.
