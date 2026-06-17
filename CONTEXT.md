# DietWise

Backend for assessing recipes from a web page and suggesting healthier, more sustainable ingredient alternatives. Serves the MyRecipeWatch mobile app and the Responsible Cooking Alliance (RCA) browser extension.

## Language

### Applications

**MyRecipeWatch** (MyRW):
The mobile app. Used to assess recipes a user cooks for themselves and their household.

**RCA** (Responsible Cooking Alliance):
The browser extension. Used by a content provider to assess recipes they *publish*.

_The same person may use both from one account, but the intent differs (cook-for-self vs. publish), so user-scoped data is kept separate per application._

### Domain

**Recipe**:
A set of ingredients and instructions extracted from a web page. Extracted fresh on each assessment and not persisted; located by its source URL.

**Ingredient**:
An item in a recipe's ingredient list. Not stably identifiable across assessments — its in-recipe id is regenerated every extraction.

**TriggerIngredient**:
Master data naming an ingredient (or class of ingredients) whose presence in a recipe can trigger a substitution Rule.
_Avoid_: trigger

**AlternativeIngredient**:
Master data naming a healthier or more sustainable substitute that a Suggestion proposes in place of a recipe ingredient.
_Avoid_: replacement, substitute

**Rule**:
Master data tying a TriggerIngredient (and optional role/technique) to a health Recommendation and the alternatives that satisfy it.

**SuggestionTemplate**:
Master data pairing a Rule with one AlternativeIngredient (plus restriction, equivalence, and technique notes) — a reusable, recipe-independent "replace X with Y" definition.

**Suggestion**:
A SuggestionTemplate instantiated against a specific recipe during assessment: it knows what it applies to (an ingredient or the whole recipe) and carries rationale, seasonality, cost, and stats. Generated fresh per assessment; never persisted.

**SuggestionDecision**:
A user's standing accept/reject verdict on one Suggestion, for one recipe. Current state (last-write-wins), not a history of actions.
_Avoid_: SavedDecision, saved suggestion, suggestion feedback, choice

**SuggestionStats**:
Aggregate counts of accept/reject *events* for a SuggestionTemplate. Answers "how often", whereas a SuggestionDecision answers "what is this user's verdict now, for this recipe".

### Backoffice

**Working Copy**:
The single shared staging area where backoffice editors' changes to master data accumulate before going live. System-wide, not per-editor; invisible to recipe assessment until published.
_Avoid_: draft, sandbox, staging

**Staged Change**:
One not-yet-published edit to master data, held in the Working Copy.
_Avoid_: pending edit, delta, diff

**Publish**:
Applying the entire Working Copy to the live master data as a single batch, after which the changes take effect in assessments.
_Avoid_: apply, commit, save, deploy

**Deactivate / Activate**:
Marking a *published* Rule or SuggestionTemplate so recipe assessment stops (resp. resumes) using it. Reversible, staged like any other change, and the entity stays visible while deactivated. Deactivating a SuggestionTemplate is how an editor "removes" it from a Rule without losing it or its history.
_Avoid_: delete, remove, disable, archive

**Discard**:
Dropping a new Rule or SuggestionTemplate that exists only in the Working Copy and was never published. Distinct from Deactivate, which applies to already-published entities.
_Avoid_: delete, cancel, undo

**Revert**:
Restoring a single changed field of an already-published entity in the Working Copy back to its live master value. Per-field; distinct from Discard, which drops an unpublished new Rule wholesale.
_Avoid_: undo, reset, rollback, cancel
