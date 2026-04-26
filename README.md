<div align="center">

# Fititoz CS3 Extensions

[![Build Status](https://github.com/fititoz/CS3-Extensions/actions/workflows/build.yml/badge.svg)](https://github.com/fititoz/CS3-Extensions/actions)

Una colección de extensiones para la aplicación [CloudStream 3](https://github.com/recloudstream/cloudstream), orientadas principalmente a contenido Hentai en español.

</div>

## 📦 Extensiones Disponibles

Actualmente este repositorio incluye los siguientes Providers:

- **TioHentai** (`TioHentaiProvider`) - [tiohentai.com](https://tiohentai.com)
- **HentaiLa** (`HentailaProvider`) - [hentaila.com](https://hentaila.com)
- **HentaiJK** (`HentaijkProvider`) - [hentaijk.com](https://hentaijk.com)
- **UnderHentai** (`UnderHentaiProvider`) - [underhentai.net](https://www.underhentai.net)
- **Hentaila.tv** (`HentailaTvProvider`) - [hentaila.tv](https://hentaila.tv/)
- **VeoHentai** (`VeoHentaiProvider`) - [veohentai.com](https://veohentai.com/)
- **EsHentai.tv** (`EsHentaiTvProvider`) - [eshentai.tv](https://eshentai.tv/)
- **LatinoHentai** (`LatinoHentaiProvider`) - [latinohentai.com](https://latinohentai.com/)

Características principales:
- ✅ Catálogo de series y ovas
- ✅ Últimos episodios agregados
- ✅ Búsqueda funcional
- ✅ Extracción de enlaces de video de múltiples servidores
- ✅ Descargas (Download Support)

## 🚀 Cómo instalar en CloudStream

> ⚠️ **IMPORTANTE:** Estas extensiones están categorizadas como **Anime** para mejorar su visibilidad, pero contienen contenido para adultos. Ya no es necesario activar la opción "Mostrar contenido NSFW" en los ajustes de CloudStream para verlas.

Puedes agregar todas estas extensiones a tu aplicación fácilmente siguiendo estos pasos:

1. Abre CloudStream y ve a **Ajustes** -> **Extensiones**.
2. Presiona en **+ Añadir Repositorio**.
3. Pega la siguiente URL:

```text
https://raw.githubusercontent.com/fititoz/CS3-Extensions/main/repo.json
```

4. Escribe un nombre para el repositorio (Ej: `Fititoz Repo`) y presiona **Añadir**.
5. ¡Listo! Ahora verás las extensiones disponibles para instalar y usar.

*(O utiliza el acceso directo desde el móvil: [Añadir a CloudStream](cloudstreamrepo://raw.githubusercontent.com/fititoz/CS3-Extensions/main/repo.json))*

## 🐛 Reporte de Errores (Bugs)
Las páginas web cambian constantemente su estructura. Si notas que una extensión deja de mostrar contenido, la búsqueda no funciona o da error "No links found", por favor abre un [Issue en este repositorio](https://github.com/fititoz/CS3-Extensions/issues) para que pueda actualizar los scrapers.

## 🛠️ Desarrollo y Compilación

Este proyecto está construido usando Kotlin y Gradle, siguiendo la [documentación oficial de desarrollo](https://recloudstream.github.io/csdocs/devs/gettingstarted/) de CloudStream.

Las builds son generadas automáticamente por GitHub Actions. Cada vez que se hace un commit en la rama `main`, la Action compila los archivos `.cs3` y `.json`, subiendo el resultado compilado de los plugins a la rama huérfana `builds`.

---
*Disclaimer: Este repositorio no aloja ningún contenido multimedia, solo provee scripts de extracción web (scrapers) para organizar los enlaces públicos provistos por terceros.*
