# Publicación en Google Play Store — Replicant303

Todos los artefactos técnicos, certificados y recursos de tienda para **Replicant303** están listos y verificados.

## 1. Estado y Recursos Generados

| Recurso | Ubicación | Descripción |
| --- | --- | --- |
| **Android App Bundle (AAB)** | `dist/Replicant303-Android-1.0.1.aab` | **Archivo a subir en Google Play Console** (13 MB, firmado) |
| **APK instalable local** | `dist/Replicant303-Android-1.0.1.apk` | Para pruebas directas en teléfono o tablet (`adb install -r`) |
| **Keystore de subida (Upload Key)** | `/home/danimanx/keystores/replicant303-upload.jks` | RSA 4096-bit (Válido hasta 2054) - **Hacer respaldo seguro** |
| **Contraseña del Keystore** | `/home/danimanx/keystores/replicant303-upload.password` | Archivo protegido (chmod 600) |
| **Base64 para CI (GitHub Secrets)** | `/home/danimanx/keystores/replicant303-upload.base64.txt` | Para firma en GitHub Actions |
| **Icono de la app (512×512)** | `store/icon-512.png` | 512×512 px PNG, <1 MB |
| **Gráfico de funciones (1024×500)** | `store/feature-graphic-1024x500.png` | 1024×500 px PNG sin canal alfa |
| **Capturas de pantalla para teléfono** | `store/screenshots/phone_*.png` | Ratio 2:1 (1080×2160 px), sin canal alfa |
| **Capturas de pantalla para tablet** | `store/screenshots/tablet_*.png` | Landscape (2560×1600) y Portrait (1600×2560) |
| **Ficha de Play Store (Español)** | `store/listing/es-419.md` | Nombre, descripción corta y completa validadas |
| **Ficha de Play Store (Inglés)** | `store/listing/en-US.md` | Listing en inglés verificado con límites de Google Play |
| **Declaración de seguridad de datos** | `store/data-safety.md` | Guía de respuestas para el formulario de Data Safety |
| **Política de privacidad** | `docs/PRIVACY.md` | URL pública para la consola |

---

## 2. Paso a Paso en Google Play Console

### Paso 1: Crear la Aplicación
1. Entra a [Google Play Console](https://play.google.com/console).
2. Haz clic en el botón azul **Crear app** (arriba a la derecha).
3. Completa los datos iniciales:
   - **Nombre de la app**: `Replicant303`
   - **Idioma predeterminado**: `Español (Latinoamérica) - es-419` o `Inglés (EE. UU.) - en-US`
   - **¿App o juego?**: `App`
   - **¿Gratis o de pago?**: `Gratis`
   - Marca las casillas de declaraciones de desarrollador (Cumplimiento de leyes de exportación de EE. UU. y Políticas del programa para desarrolladores).
   - Haz clic en **Crear app**.

---

### Paso 2: Configurar la Ficha de Play Store Principal (Main Store Listing)
En el menú lateral izquierdo: **Crecimiento** -> **Presencia en Google Play Store** -> **Ficha de Play Store principal**:

1. **Detalles de la app**:
   - **Nombre de la app**: `Replicant303`
   - **Descripción corta**:
     > `Sintetizador de bajos acid 303 analógico, secuenciador por pasos y MIDI USB.`
   - **Descripción completa**: Copia el texto completo de [`store/listing/es-419.md`](file:///PROCESOS/Replicant303Android/store/listing/es-419.md).
   *(Opcional: agrega traducción en Inglés `en-US` con [`store/listing/en-US.md`](file:///PROCESOS/Replicant303Android/store/listing/en-US.md))*.

2. **Recursos gráficos**:
   - **Ícono de la app**: Sube [`store/icon-512.png`](file:///PROCESOS/Replicant303Android/store/icon-512.png).
   - **Gráfico de funciones**: Sube [`store/feature-graphic-1024x500.png`](file:///PROCESOS/Replicant303Android/store/feature-graphic-1024x500.png).
   - **Teléfono**: Sube las capturas de [`store/screenshots/phone_*.png`](file:///PROCESOS/Replicant303Android/store/screenshots/) (sube al menos 4-6 capturas representativas).
   - **Tablet de 7 y 10 pulgadas**: Sube [`store/screenshots/tablet_landscape.png`](file:///PROCESOS/Replicant303Android/store/screenshots/tablet_landscape.png) y [`store/screenshots/tablet_portrait.png`](file:///PROCESOS/Replicant303Android/store/screenshots/tablet_portrait.png).
   - Haz clic en **Guardar**.

---

### Paso 3: Configurar Contenido de la App (Políticas y Declaraciones)
En el menú lateral izquierdo: **Política** -> **Contenido de la app**:

Completa cada sección siguiendo [`store/data-safety.md`](file:///PROCESOS/Replicant303Android/store/data-safety.md):

1. **Política de privacidad**:
   - URL: `https://github.com/DanimanX/Replicant303Android/blob/main/docs/PRIVACY.md`
2. **Acceso a la app**:
   - Selecciona: *"Todas las funciones están disponibles sin restricciones especiales o credenciales"*.
3. **Anuncios**:
   - Selecciona: *"No, mi app no contiene anuncios"*.
4. **Clasificación del contenido (IARC)**:
   - Inicia el cuestionario.
   - Categoría: *Utilidad, Productividad, Comunicación u Otro* (o Música/Audio).
   - Responde **NO** a violencia, sexualidad, lenguaje ofensivo, drogas, apuestas, compartir ubicación o chats de usuarios.
   - Resultado: Apta para todo público (PEGI 3 / Para todos).
5. **Público objetivo**:
   - Selecciona edades: *13-15, 16-17, 18 y mayores*.
   - ¿Podría atraer involuntariamente a niños?: *No*.
6. **Seguridad de los datos (Data safety)**:
   - ¿Tu app recopila o comparte datos de usuarios?: **NO**.
   - (El cuestionario termina de inmediato ya que no recopila nada).
7. **Apps de noticias / COVID-19 / Gubernamentales / Servicios financieros**:
   - Marca **NO** en todas.

---

### Paso 4: Crear y Subir el Release (AAB)

1. En el menú lateral izquierdo:
   - Para cuenta personal nueva: ve a **Pruebas** -> **Pruebas cerradas** (Closed testing) o **Pruebas internas** (Internal testing).
   - Para cuenta con acceso directo a producción: ve a **Versiones** -> **Producción**.
2. Haz clic en **Crear nueva versión**.
3. **Firma de aplicaciones de Google Play (Play App Signing)**:
   - Deja activada la opción predeterminada de Google Play App Signing (Google registrará tu clave de subida en este primer upload).
4. **Paquetes de aplicaciones**:
   - Arrastra y suelta el archivo:
     [`dist/Replicant303-Android-1.0.1.aab`](file:///PROCESOS/Replicant303Android/dist/Replicant303-Android-1.0.1.aab)
5. **Nombre de la versión**:
   - Se rellenará automáticamente como `1.0.1 (10001)`.
6. **Notas de la versión**:
   - Copia una breve nota (ejemplo: *"Primera versión de Replicant303: sintetizador de bajos acid analógico en tiempo real con secuenciador de 16 pasos y soporte MIDI USB."*).
7. Haz clic en **Siguiente** -> **Revisar versión** -> **Iniciar el lanzamiento**.

¡Listo! La versión quedará en revisión por Google.
