# Pointage Online Days

Application Spring Boot + Angular pour la gestion des jours de télétravail.

## Fonctionnalités

- Compte + login (JWT, sessions par token)
- Calendrier de réservation des jours online
  - Maximum **2 jours / semaine**
  - **Vendredi interdit**
  - **Pas de jours consécutifs** (lundi+mardi impossibles)
- Rapport obligatoire chaque jour travaillé à distance avant **18h00 (heure de Tunis)**
  - Sans rapport avant l'échéance → le jour est compté comme **non travaillé**
- Rapports visibles uniquement par l'**admin**
- Statistiques hebdomadaires : l'utilisateur voit les siennes, l'admin voit celles de chacun

## Stack

| | |
|---|---|
| Backend | Spring Boot 3.2.5, Java 17, Maven |
| Frontend | Angular 18 (standalone), SCSS |
| Base de données | MySQL (WAMP), base `online_days` |

## Prérequis

- Java 17
- WAMP démarré (MySQL sur `localhost:3306`, user `root`, pas de mot de passe)
- Node.js 18+ et Angular CLI (`npm i -g @angular/cli`)

## Lancer le backend

```bash
cd backend
mvnw.cmd spring-boot:run
```

- La base `online_days` est créée automatiquement au démarrage.
- Un admin de démo est créé automatiquement :
  - **email**: `admin@pointage.tn`
  - **mot de passe**: `admin123`

Le scheduler passe à **18:05** heure de Tunis (Africa/Tunis) et marque `MISSED` les jours
sans rapport soumis. La vérification du délai est aussi faite côté serveur lors de la soumission.

## Lancer le frontend

```bash
cd frontend
npm install
ng serve
```

## Créer un utilisateur

S'inscrire depuis `http://localhost:4200/register`. Les comptes créés sont des `USER` par défaut.
Pour créer un admin supplémentaire, modifier manuellement la colonne `role` en `ADMIN` dans la base.

## Règles métier (détail)

| Règle | Implémentation |
|---|---|
| Max 2 jours / semaine | Comptage Lundi→Dimanche, vérifié backend |
| Vendredi interdit | Vérifié backend + désactivé dans le calendrier |
| Week-end interdit | Vérifié backend + désactivé dans le calendrier |
| Pas de jours consécutifs | Vérifié backend (la veille/le lendemain déjà réservés) |
| Deadline 18h00 Africa/Tunis | Scheduler quotidien `0 5 18 * * *` zone `Africa/Tunis` |
| Jour sans rapport = non travaillé | Statut `MISSED` posé par le scheduler |

## API principales

| Méthode | Endpoint | Rôle |
|---|---|---|
| POST | `/api/auth/register` | public |
| POST | `/api/auth/login` | public |
| POST | `/api/auth/refresh` | public |
| POST | `/api/online-days` | USER |
| GET | `/api/online-days?start=YYYY-MM-DD` | USER |
| DELETE | `/api/online-days/{id}` | USER |
| POST | `/api/reports` | USER |
| GET | `/api/reports/my?start=YYYY-MM-DD` | USER |
| GET | `/api/stats/my?start=YYYY-MM-DD` | USER |
| GET | `/api/admin/stats?start=YYYY-MM-DD` | ADMIN |
| GET | `/api/admin/reports?start=YYYY-MM-DD` | ADMIN |