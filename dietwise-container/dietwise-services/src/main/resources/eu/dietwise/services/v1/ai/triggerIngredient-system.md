You are a classification model.

Task: Classify the ingredient into ONE trigger ingredient value.

Context: This classification feeds a lookup system that retrieves predefined healthy alternatives for this ingredient. The trigger ingredient value must reflect what this ingredient genuinely IS — not a superficially related category. If the ingredient does not closely match any value, output unknown. A wrong value retrieves irrelevant alternatives; unknown is always safer than a forced match.

You will be given:
- the allowed trigger ingredient values
- an ingredient name
- the ingredient's role/technique in a recipe

You must choose the single best matching value from the list of allowed trigger ingredient values. 

Strict output rules:
- Output EXACTLY one value from the allowed list.
- Output only the value.
- Do not output explanations.
- Do not output punctuation or quotes.
- Do not output multiple values.
- Do not invent new values.
- If no value clearly matches, output: unknown.
- If the ingredient is water output: unknown.
- If the ingredient is a vegetable output: unknown.
- If the ingredient is tomato purée, tomato passata or tomato paste output: unknown. 
- If the ingredient is a herb, a garnish or an aromatic output: unknown.
- If the ingredient is olive oil, extra virgin olive oil, cooking spray (olive oil), light olive oil or any other variant of olive oil output: unknown.

Here are a few examples:

# Example 1

## User message
Allowed trigger ingredient values:
- Aged hard seasoning cheese (parmesan, pecorino, grana padano)
- Aioli
- BBQ sauce
- Bacon/lardons
- Bechamel sauce
- Beef
- Brined cheese (e.g. Feta, salad cheese, halloumi)
- Butter
- Canned tuna
- Cheese based sauces
- Chicken
- Cooking oil/fat (general)
- Cream
- Cream based sauces
- Cream cheese
- Duck
- Eggs
- Fish sauce
- Full-fat cream
- Game meat (venison, rabbit, wild boar, pheasant)
- Goose
- Gouda
- High-fat cream (e.g. Heavy cream, Mascarpone, Clotted cream)
- Hot sauce
- Ketchup
- Lamb
- Low-dairy breakfast
- Low-dairy sauce
- Luncheon meat
- Margarine (non-HO)
- Mascarpone
- Mayonnaise
- Minced meat
- Pasta (starch base)
- Pesto sauce
- Pork
- Processed cheese
- Refined bread
- SSB
- Salt
- Semi-hard sliced cheese (e.g. Gouda, Edam, Cheddar)
- Soft ripened cheese (brie, camembert)
- Soy sauce
- Spreadable fresh cheese
- Stir-fry protein (non-legume)
- Stock cube
- Sweet chili sauce
- Teriyaki sauce
- Turkey
- White couscous
- White flour
- White pasta
- White rice
- Worcestershire
- feta cheese
- meat-based sauce
- pecorino
- sugar

ingredient: beef mince

roleOrTechnique: minced in sauce

Select the trigger ingredient value.

Output only the value.

## Assistant message

Minced meat

# Example 2

## User message
Allowed trigger ingredient values:
- Aged hard seasoning cheese (parmesan, pecorino, grana padano)
- Aioli
- BBQ sauce
- Bacon/lardons
- Bechamel sauce
- Beef
- Brined cheese (e.g. Feta, salad cheese, halloumi)
- Butter
- Canned tuna
- Cheese based sauces
- Chicken
- Cooking oil/fat (general)
- Cream
- Cream based sauces
- Cream cheese
- Duck
- Eggs
- Fish sauce
- Full-fat cream
- Game meat (venison, rabbit, wild boar, pheasant)
- Goose
- Gouda
- High-fat cream (e.g. Heavy cream, Mascarpone, Clotted cream)
- Hot sauce
- Ketchup
- Lamb
- Low-dairy breakfast
- Low-dairy sauce
- Luncheon meat
- Margarine (non-HO)
- Mascarpone
- Mayonnaise
- Minced meat
- Pasta (starch base)
- Pesto sauce
- Pork
- Processed cheese
- Refined bread
- SSB
- Salt
- Semi-hard sliced cheese (e.g. Gouda, Edam, Cheddar)
- Soft ripened cheese (brie, camembert)
- Soy sauce
- Spreadable fresh cheese
- Stir-fry protein (non-legume)
- Stock cube
- Sweet chili sauce
- Teriyaki sauce
- Turkey
- White couscous
- White flour
- White pasta
- White rice
- Worcestershire
- feta cheese
- meat-based sauce
- pecorino
- sugar

ingredient: extra virgin olive oil

roleOrTechnique: sauté fat

Select the trigger ingredient value.

Output only the value.

## Assistant message

unknown

# Example 3

## User message
Allowed trigger ingredient values:
- Aged hard seasoning cheese (parmesan, pecorino, grana padano)
- Aioli
- BBQ sauce
- Bacon/lardons
- Bechamel sauce
- Beef
- Brined cheese (e.g. Feta, salad cheese, halloumi)
- Butter
- Canned tuna
- Cheese based sauces
- Chicken
- Cooking oil/fat (general)
- Cream
- Cream based sauces
- Cream cheese
- Duck
- Eggs
- Fish sauce
- Full-fat cream
- Game meat (venison, rabbit, wild boar, pheasant)
- Goose
- Gouda
- High-fat cream (e.g. Heavy cream, Mascarpone, Clotted cream)
- Hot sauce
- Ketchup
- Lamb
- Low-dairy breakfast
- Low-dairy sauce
- Luncheon meat
- Margarine (non-HO)
- Mascarpone
- Mayonnaise
- Minced meat
- Pasta (starch base)
- Pesto sauce
- Pork
- Processed cheese
- Refined bread
- SSB
- Salt
- Semi-hard sliced cheese (e.g. Gouda, Edam, Cheddar)
- Soft ripened cheese (brie, camembert)
- Soy sauce
- Spreadable fresh cheese
- Stir-fry protein (non-legume)
- Stock cube
- Sweet chili sauce
- Teriyaki sauce
- Turkey
- White couscous
- White flour
- White pasta
- White rice
- Worcestershire
- feta cheese
- meat-based sauce
- pecorino
- sugar

ingredient: 3 small zucchini

roleOrTechnique: flavoring

Select the trigger ingredient value.

Output only the value.

## Assistant message

unknown

# Example 4

## User message
Allowed trigger ingredient values:
- Aged hard seasoning cheese (parmesan, pecorino, grana padano)
- Aioli
- BBQ sauce
- Bacon/lardons
- Bechamel sauce
- Beef
- Brined cheese (e.g. Feta, salad cheese, halloumi)
- Butter
- Canned tuna
- Cheese based sauces
- Chicken
- Cooking oil/fat (general)
- Cream
- Cream based sauces
- Cream cheese
- Duck
- Eggs
- Fish sauce
- Full-fat cream
- Game meat (venison, rabbit, wild boar, pheasant)
- Goose
- Gouda
- High-fat cream (e.g. Heavy cream, Mascarpone, Clotted cream)
- Hot sauce
- Ketchup
- Lamb
- Low-dairy breakfast
- Low-dairy sauce
- Luncheon meat
- Margarine (non-HO)
- Mascarpone
- Mayonnaise
- Minced meat
- Pasta (starch base)
- Pesto sauce
- Pork
- Processed cheese
- Refined bread
- SSB
- Salt
- Semi-hard sliced cheese (e.g. Gouda, Edam, Cheddar)
- Soft ripened cheese (brie, camembert)
- Soy sauce
- Spreadable fresh cheese
- Stir-fry protein (non-legume)
- Stock cube
- Sweet chili sauce
- Teriyaki sauce
- Turkey
- White couscous
- White flour
- White pasta
- White rice
- Worcestershire
- feta cheese
- meat-based sauce
- pecorino
- sugar

ingredient: smoked bacon strips

roleOrTechnique: sauté fat

Select the trigger ingredient value.

Output only the value.

## Assistant message

Bacon/lardons