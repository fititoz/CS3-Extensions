<div align="center">

# Fititoz CS3 Extensions

Una colección de extensiones para la aplicación [CloudStream 3](https://github.com/recloudstream/cloudstream), orientadas principalmente a contenido Hentai en español.

</div>

## 📦 Extensiones Disponibles

Actualmente este repositorio incluye las siguientes extensiones (Providers):

- **TioHentai** (`TioHentaiProvider`) - [tiohentai.com](https://tiohentai.com)
- **HentaiLa** (`HentailaProvider`) - [hentaila.com](https://hentaila.com)
- **HentaiJK** (`HentaijkProvider`) - [hentaijk.com](https://hentaijk.com)

Características principales soportadas:
- ✅ Catálogo de series y ovas
- ✅ Últimos episodios agregados
- ✅ Búsqueda funcional
- ✅ Extracción de enlaces de video de múltiples servidores
- ✅ Descargas (Download Support)

## 🚀 Cómo instalar en CloudStream

Puedes agregar todas estas extensiones a tu aplicación de CloudStream fácilmente siguiendo estos pasos:

1. Abre CloudStream y ve a **Ajustes** -> **Extensiones**.
2. Presiona en **+ Añadir Repositorio**.
3. Pega la siguiente URL:

```text
https://raw.githubusercontent.com/fititoz/CS3-Extensions/builds/repo.json
```

4. Escribe un nombre para el repositorio (Ej: `Fititoz Repo`) y presiona **Añadir**.
5. ¡Listo! Ahora verás las extensiones disponibles para instalar y usar.

*(También puedes usar este atajo directamente desde tu móvil: [Añadir a CloudStream](cloudstreamrepo://raw.githubusercontent.com/fititoz/CS3-Extensions/builds/repo.json))*

## 🛠️ Desarrollo y Compilación

Este proyecto está construido usando Kotlin y Gradle, siguiendo la [documentación oficial de desarrollo](https://recloudstream.github.io/csdocs/devs/gettingstarted/) de CloudStream.

Las builds son generadas automáticamente por GitHub Actions. Cada vez que se hace un commit en la rama `main`, la Action compila los archivos `.cs3` y los sube a la rama huérfana `builds`.

---
*Disclaimer: Este repositorio no aloja ningún contenido multimedia, solo provee scripts de extracción web (scrapers) para organizar los enlaces públicos provistos por terceros.*
