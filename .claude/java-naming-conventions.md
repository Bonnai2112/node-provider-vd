# Conventions de nommage Java

Règles de nommage et d'organisation pour ce projet. À respecter dans tout code Java produit ou modifié.

## Casse

| Élément | Convention | Exemple |
|---|---|---|
| Classe, interface, enum, record | `PascalCase` | `OrderService`, `PaymentStatus` |
| Méthode, variable, paramètre | `camelCase` | `calculateTotal`, `customerId` |
| Constante (`static final`) | `UPPER_SNAKE_CASE` | `MAX_RETRY_COUNT` |
| Package | `lowercase`, sans séparateur | `com.acme.order.internal` |
| Type générique | une lettre majuscule | `T`, `E`, `K`, `V`, `R` |
| Module (Spring Modulith) | nom de domaine, singulier | `order`, `inventory` |

## Classes et interfaces

- Les classes sont des **noms** ou groupes nominaux : `Invoice`, `HttpConnectionPool`.
- Pas de préfixe `I` sur les interfaces (`IOrderService` interdit). Pour une capacité, l'adjectif en `-able` est idiomatique : `Comparable`, `Closeable`.
- Pas de suffixe `Impl` quand il n'existe qu'une seule implémentation : c'est le signe que l'interface est inutile. Avec plusieurs implémentations, nommer par ce qui les distingue : `SmtpEmailSender`, `SesEmailSender` derrière `EmailSender`.
- Suffixes porteurs de sens, à utiliser pour ce qu'ils signifient : `*Service`, `*Repository`, `*Controller`, `*Factory`, `*Exception`, `*Config`.
- Suffixes interdits car vides de sens : `*Manager`, `*Helper`, `*Util`, `*Processor`, `*Handler`. Nommer la classe par ce qu'elle fait réellement.

## Méthodes

- Les méthodes sont des **verbes** ou groupes verbaux.
- Accesseurs : `getTotal()`, `isActive()` / `hasChildren()` pour les booléens.
- Création : choisir **un seul** verbe pour tout le projet. Factory statiques : `of()` pour des valeurs, `from()` pour une conversion, `valueOf()` pour du parsing.
- Conversion : `toList()` (nouvelle copie) vs `asMap()` (vue partagée). Respecter la nuance `to` / `as`.
- Ne pas mélanger mutation et requête dans une même méthode. Une méthode `void` mute, une méthode qui retourne une valeur n'a pas d'effet de bord.
- Booléens nommés positivement, comme des assertions : `isEmpty()`, `canRetry()`, `shouldNotify()`. Jamais de négation dans le nom (`isNotReady()` interdit).

## Variables

- Le nom porte l'**intention** ; sa longueur est proportionnelle à sa portée.
- Abréviations cryptiques interdites (`usrCnt`, `tmpVal`) : écrire `userCount`.
- Noms « bruités » interdits seuls : `data`, `info`, `value`, `obj`, `temp`, `result`.
- Pas de notation hongroise (`strName`, `iCount`) : le type est déjà dans la signature.
- Pas de suffixes numériques (`order1`, `order2`) : ils signalent deux concepts mal distingués.
- Collections au pluriel : `orders`. Une `Map` nomme ses deux côtés : `customerById`, `pricesByRegion`.
- Ne pas mettre le type dans le nom (`orderList` devient faux si la structure change) — sauf si la structure est l'information clé (`customerById`).

## Constantes et enums

- Constantes en `UPPER_SNAKE_CASE`, réservées aux vraies constantes — pas pour de la config injectable.
- Nom d'enum au singulier (`Color`, pas `Colors`), valeurs en `UPPER_SNAKE_CASE` : `Status.ACTIVE`.

## Packages et organisation

- Tout en minuscules, sans underscore ni camelCase.
- Découpage **par feature/domaine**, pas par couche technique :

```
com.acme.order            (par domaine — recommandé)
com.acme.order.internal
com.acme.inventory

com.acme.controller       (par couche — à éviter)
com.acme.service
com.acme.repository
```

- Le découpage par domaine colocalise ce qui change ensemble et permet l'encapsulation via le sous-package `internal`.

## Tests

- Classe de test : `ClasseTestée + Test` → `OrderServiceTest`. Tests d'intégration : suffixe `IT`.
- Pas de `testXxx()`. Adopter une convention expressive et s'y tenir :
  - `should_returnEmptyList_when_noOrdersExist()`
  - `givenExpiredCard_whenCharging_thenThrowsPaymentException()`
- Ou `@DisplayName("phrase lisible")`. Le style importe peu, la cohérence projet est obligatoire.

## Génériques

- Lettres conventionnelles : `T` (type), `E` (élément), `K`/`V` (clé/valeur), `R` (retour).
- Nom explicite court permis si le rôle métier est fort : `<REQ, RESP>`. Rester sobre.

## Règles transverses

- **Code monolingue en anglais**, même si le métier est en français. Ne pas mélanger `calculerTotal()` et `getCustomer()`. Seule exception : termes métier intraduisibles d'un domaine réglementé, à documenter.
- **Vocabulaire aligné sur le domaine** (Ubiquitous Language). Si le métier dit « bénéficiaire », la classe est `Beneficiary`.
- Une convention médiocre appliquée partout vaut mieux qu'une excellente appliquée à moitié.
- Faire vérifier ces règles par l'outillage (Checkstyle, ArchUnit), pas seulement par la revue humaine.
