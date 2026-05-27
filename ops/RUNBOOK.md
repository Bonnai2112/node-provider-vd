# Run-book : production des tarballs de template EL

Ce dossier porte le job offline qui prépare le tarball `templates/{network}-{el}.tar.zst`
restauré par la plateforme au provisioning de nouveaux nœuds. Cf. la décision
`2026-05-11-ext4-tarball-template-restore` dans `decisions.yaml`.

## Contrat

Un tarball par couple `(network, el-client)` supporté en restauration côté
plateforme. À ce jour : **geth** uniquement.

Layout attendu sur l'hôte :

```
/var/lib/platform/
├── templates/
│   ├── hoodi-geth.tar.zst
│   └── sepolia-geth.tar.zst
└── nodes/
    └── <node-uuid>/
        ├── eth-docker/   ← clone eth-docker (compose files, .env)
        └── data/         ← bind-mount EL chaindata
```

## Ajouter un nouveau couple

Pour chaque couple à templatiser il faut un **nœud frozen** dédié — un nœud
provisionné par la plateforme qu'on accepte d'arrêter quelques minutes par jour
pour produire le tarball.

### 1. Provisionner le nœud frozen

Via l'UI ou l'API REST. Note l'UUID retourné (`<frozen-node-uuid>`). Attends
qu'il soit `Ready` (sync EL terminée). Pour Hoodi/Sepolia, prévoir plusieurs
heures à la première mise en service.

### 2. Déployer le job ops

```sh
# Code (script + units) à pousser à l'emplacement attendu par les units.
sudo install -d /opt/platform/ops
sudo install -m 0755 ops/produce-el-template.sh /opt/platform/ops/
sudo install -m 0644 ops/RUNBOOK.md /opt/platform/ops/
sudo install -m 0644 ops/systemd/produce-el-template@.service /etc/systemd/system/
sudo install -m 0644 ops/systemd/produce-el-template@.timer /etc/systemd/system/
sudo systemctl daemon-reload
```

### 3. Config de l'instance

```sh
sudo install -d -m 0750 /etc/platform/templates
sudo cp ops/example-hoodi-geth.env /etc/platform/templates/hoodi-geth.env
sudo $EDITOR /etc/platform/templates/hoodi-geth.env
# Remplir FROZEN_NODE_DIR et EL_DATA_DIR avec l'UUID du nœud frozen.
sudo chmod 0640 /etc/platform/templates/hoodi-geth.env
```

### 4. Premier run + activation du timer

```sh
# Vérifier que le script tourne avant d'enclencher le timer.
sudo systemctl start produce-el-template@hoodi-geth.service
sudo journalctl -u produce-el-template@hoodi-geth.service -f

# Une fois validé : enclencher le timer (1x/jour à 03:00).
sudo systemctl enable --now produce-el-template@hoodi-geth.timer
systemctl list-timers produce-el-template@*.timer
```

## Comportement attendu

- Pendant le run : conteneur `execution` du nœud frozen stoppé, le CL continue.
  Downtime EL ≈ durée de `tar+zstd` (~5–20 min selon chaindata, niveau zstd,
  matériel). Le `tar+zstd` sature le disque source (`%util` ≈ 95 %,
  ~130–150 MB/s) : si `templates/` et `nodes/` partagent le même disque, les
  autres nodes vus depuis cet hôte subiront un ralentissement I/O pendant la
  fenêtre. Cf. § *Tuning I/O disque* dans
  [`bc-node-lifecycle/README.md`](../bc-node-lifecycle/README.md) pour les
  options de séparation de disques.
- Si `tar` échoue, le `.tmp` est nettoyé et le tarball précédent reste en place
  (rename atomique non déclenché). Le conteneur EL est toujours redémarré par
  un trap shell.
- Un provisioning concurrent ne lit jamais un fichier tronqué grâce au rename.

## Retirer un couple

```sh
sudo systemctl disable --now produce-el-template@hoodi-geth.timer
sudo rm /etc/platform/templates/hoodi-geth.env
sudo rm /var/lib/platform/templates/hoodi-geth.tar.zst  # optionnel
```

Les nouveaux provisionnings retombent en sync from-scratch tant qu'aucun
tarball n'est présent pour ce couple — comportement nominal du fallback.
