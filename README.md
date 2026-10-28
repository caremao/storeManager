# StoreManager

Tiendas e inventarios para una mesa presencial de D&D 5e (reglas de 2014).

- **El GM** arma tiendas con su stock y sus precios (a mano o desde un CSV), las abre o cierra,
  aprueba o regatea lo que piden los jugadores, y registra ventas en la mesa. Cada venta descuenta
  stock y mueve las monedas.
- **Cada jugador** escanea el QR de su personaje una vez. Desde el teléfono ve su bolsa y su
  inventario, los administra, y compra o vende en las tiendas abiertas.
- **Cada tienda** tiene además un QR de vitrina: cualquiera que lo escanee ve lo que hay a la venta,
  sin sesión. Lo que el GM marca como oculto no aparece.
- Todo se actualiza en vivo por SSE y queda en el historial.

## Stack

| Capa | Tecnología |
|---|---|
| Backend | Java 21 (Corretto), Spring Boot 4.1, Spring Data JPA, Flyway, H2 en archivo |
| Frontend | React 19, TypeScript, Vite 7, Tailwind 4, TanStack Query |
| Tiempo real | Server-Sent Events (`/api/events`) |
| Catálogo | SRD 5.1 incorporado (599 items), sin llamadas externas |
| Empaquetado | Una sola imagen Docker: Spring Boot sirve la SPA |

## Desarrollo

Requisitos: JDK 21 y Node ≥ 20.19 (Vite 7). En TWO el JDK está en `C:\Users\carem\.jdks\corretto-21.0.11`.

```bash
cd backend && JAVA_HOME=~/.jdks/corretto-21.0.11 ./mvnw spring-boot:run
```

```bash
cd frontend && npm install && npm run dev
```

Vite reenvía `/api` a `localhost:8080`, así la cookie de sesión funciona igual que en producción.
La base de desarrollo queda en `backend/data/`. La documentación OpenAPI está en `/api/openapi.json`.

### Tests

```bash
cd backend && ./mvnw verify
```

- **Tests unitarios:** monedas (`PurseTest`, derivado de `test_currency.py` del proyecto original),
  precios, historial y slugs.
- **Tests de integración (`*IT`):** levantan la app y hablan por HTTP real, con cookies y SSE. Cubren
  el flujo de compra con aprobación, la compra directa, la venta, los permisos por rol y la carrera por
  la última unidad, el regateo, el valor oculto, la vitrina, los objetos ocultos y la importación
  CSV. `SecurityIT`, `ValidationIT` y `UntrustedProxyIT` prueban entradas hostiles: pedidos de otros
  sitios, cuerpos gigantes, rotación de IPs en el login, números fuera de rango.
- **Migraciones (`MigrationTest`):** migra una base con datos como los de THREE hasta la última versión.

```bash
cd frontend && npm run typecheck && npm run lint && npm test && npm run build
```

Vitest cubre el formato de monedas, el historial y la lectura de CSV.

## Cómo funciona

- **Inventarios.** Tiendas y personajes son inventarios: cada uno tiene items, monedas
  (`copper`/`silver`/`gold`/`platinum`) e historial.
- **Monedas.** Todo se guarda en piezas de cobre; cada precio recuerda en qué moneda se fijó (ppt, po,
  pp o pc) y se muestra en ella. La bolsa permite convertir entre monedas.
- **Valor real y percibido.** El GM fija el valor real de cada objeto. El jugador ve lo que cree que
  vale ("desconocido" o estimado) hasta que el GM se lo revela. Las tiendas tasan con el valor real.
- **Acceso.**
  - El GM entra con la contraseña de la campaña (8 a 72 bytes, guardada con bcrypt). No hay una
    contraseña maestra por encima de las campañas.
  - Cada personaje tiene un token de 128 bits. El QR lleva a `/join#<token>`: al ir en el fragmento,
    el navegador no lo envía al servidor y no queda en logs. La SPA lo cambia por una cookie `HttpOnly`.
  - "Nuevo QR" revoca el anterior y cierra las sesiones que lo usaban.
  - La vitrina de cada tienda (`/vitrina#<token>`) es pública y de solo lectura: muestra nombre,
    precio y stock de lo que está a la vista, nunca la caja, las notas ni lo oculto. Tiene su propio
    "Nuevo QR". Si quien la abre ya es jugador de la campaña, puede ir al mostrador desde ahí.
- **Compras y ventas.**
  - El jugador pide desde el mostrador. Si la tienda exige aprobación, el pedido queda pendiente
    hasta que el GM lo aprueba. Si no la exige, las compras a precio de lista se ejecutan al instante.
  - Las ventas de un jugador a una tienda siempre esperan al GM; la tienda ofrece el valor real por
    el porcentaje de recompra.
  - **Regateo.** En los productos negociables y en las ventas, el jugador puede ofrecer otro precio.
    El GM acepta, contraoferta o rechaza, y así por turnos hasta cerrar o cancelar.
  - La ejecución (`TradeService.execute`) mueve stock, monedas e historial en una sola transacción.
    Los `@Version` evitan vender dos veces la última unidad.
- **Importación CSV.** En cada inventario, el GM descarga una plantilla (`;`, UTF-8 con BOM, para
  Excel en español) y sube su CSV. La vista previa marca los errores por fila; la importación es
  todo o nada y suma la cantidad a los items que ya existen con el mismo nombre. La columna `srd`
  completa lo que falte desde el catálogo.
- **Reglas de la mesa.** El GM decide, desde el panel, si los jugadores pueden sumarse monedas solos.
  Gastar siempre pueden.
- **Seguridad.**
  - Las escrituras a la API que no vienen de la propia app se rechazan, según `Sec-Fetch-Site` o
    `Origin`. `SameSite` no alcanza, porque Foundry y los demás `*.srv.home.arpa` son "same-site".
  - Ningún pedido puede pasar de 256 KB (`app.max-request-bytes`).
  - El login del GM admite 5 fallos por IP y 30 por campaña cada 15 minutos. Solo se cree en
    `X-Forwarded-For` cuando llega de loopback o de una red de Docker, es decir, de Funnel o de Caddy.
  - Cantidades, precios y monedas tienen topes (ver `common/Limits.java`) y la aritmética no desborda.
  - Todas las respuestas llevan CSP estricta, `X-Frame-Options: DENY`, `nosniff` y
    `Referrer-Policy: no-referrer`. Por HTTPS llevan además HSTS.
- **Precios.**
  - La tienda vende a `valor × sellMarkupPct` y compra a `valor × buybackPct` (por defecto 100 % y 50 %).
  - Al pagar se usan las monedas que hay y solo se rompe una moneda grande si hace falta dar cambio.

## Despliegue en THREE

La imagen se construye en THREE desde una copia del código en `~/projects/storemanager`. El share
`claude` no es legible para `perse-phone`. El servicio corre desde `/opt/stacks/storemanager`, con
los datos en `./data`, que está sobre ZFS (`nass/stacks`).

1. Copiar el código y construir la imagen (desde TWO):

   ```bash
   tar -cf - --exclude=./.git --exclude=./.claude --exclude=./backend/target --exclude=./frontend/node_modules . | ssh perse-phone@three.home.arpa 'mkdir -p ~/projects/storemanager && tar -xf - -C ~/projects/storemanager'
   ```

   ```bash
   ssh perse-phone@three.home.arpa 'cd ~/projects/storemanager && docker build -t storemanager:0.1.0 .'
   ```

2. Crear el stack (en la shell de three). `data` tiene que ser escribible por `1000:1884`:

   ```bash
   mkdir -p /opt/stacks/storemanager/data && chgrp nas-user /opt/stacks/storemanager/data && chmod 2770 /opt/stacks/storemanager/data
   ```

   ```bash
   cp ~/projects/storemanager/deploy/compose.yaml /opt/stacks/storemanager/ && cd /opt/stacks/storemanager && docker compose up -d
   ```

3. Publicarla en la LAN y el tailnet en `https://tienda-dnd.srv.home.arpa`:

   ```bash
   /opt/stacks/caddy/add-service.sh tienda-dnd 8094
   ```

   Con esto los QR ya funcionan en la red local, para los teléfonos que tienen la CA de mkcert y están
   en una VLAN que llega a THREE.

4. Arranque automático, como los otros `*-stack.service`. Ver `deploy/storemanager-stack.service`.

### Exponer a internet (Tailscale Funnel)

Los teléfonos de los jugadores no tienen la CA de mkcert y la red de invitados no llega a THREE. Por
eso, para jugar desde cualquier red, se publica con Funnel en el puerto 8443 (el 443 es de Foundry):

1. Exigir clave para crear campañas, una vez creada la de la mesa. En `/opt/stacks/storemanager/.env`:
   `CAMPAIGN_CREATION_KEY=<algo largo>`.
2. Apuntar los QR a la URL pública: en el mismo `.env`,
   `APP_PUBLIC_BASE_URL=https://three.tail181f18.ts.net:8443`. Después, `docker compose up -d`.
3. Activar Funnel:

   ```bash
   sudo tailscale funnel --bg --https=8443 http://127.0.0.1:8094
   ```

`APP_PUBLIC_BASE_URL` decide adónde apuntan los QR. Los links se arman al mostrarlos, así que al
cambiarla los QR de la pantalla "Mesa" se actualizan solos. Los teléfonos que ya entraron conservan su
sesión, pero en el otro host.

## Licencias

Ver [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md): partes derivadas de `linuxlewis/dnd-inventory-manager`
(MIT) y el contenido del SRD 5.1 (CC-BY-4.0).
