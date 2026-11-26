package com.example.ui.components

import com.example.ui.viewmodel.VistaMapaClima
import java.util.Locale

/**
 * URLs y script del mapa Windy de Clima.
 *
 * El embed (embed.windy.com) se carga una vez por capa; los cambios de ubicación no lo recargan:
 * [scriptVolar] usa el Leaflet interno de la página (`W.map.map`, Leaflet 1.4, zoom máx. 11) para
 * un `flyTo` animado y reubica el marcador con el picker de Windy. Si esa API interna no está
 * (página cargando o Windy cambió), el script devuelve otra cosa que "ok" y se recarga la URL con
 * la vista nueva, que ya trae el zoom correcto.
 */
object WindyEmbed {
    /** Duración del vuelo de la cámara (s): suficiente para seguir el desplazamiento sin demorar. */
    const val DURACION_VUELO_S = 1.6

    private fun num(v: Double) = String.format(Locale.ROOT, "%.5f", v)

    fun urlEmbed(vista: VistaMapaClima, overlay: String): String {
        val lat = num(vista.lat)
        val lon = num(vista.lon)
        return "https://embed.windy.com/embed2.html?lat=$lat&lon=$lon&zoom=${vista.zoom}&level=surface&overlay=$overlay" +
            "&menu=&message=&marker=true&calendar=now&pressure=&type=map&location=coordinates&detail=" +
            "&detailLat=$lat&detailLon=$lon&metricWind=km%2Fh&metricTemp=%C2%B0C&metricRain=mm&radarRange=-1&product=ecmwf"
    }

    /** windy.com completo (diálogo ampliado): admite más zoom que el embed, se usa uno más. */
    fun urlCompleta(vista: VistaMapaClima, overlay: String): String =
        "https://www.windy.com/?$overlay,${num(vista.lat)},${num(vista.lon)},${vista.zoom + 1}"

    /** Script de vuelo animado; devuelve "ok" si pudo usar el mapa de la página. Solo números: no hay texto del usuario. */
    fun scriptVolar(vista: VistaMapaClima): String {
        val lat = num(vista.lat)
        val lon = num(vista.lon)
        return "(function(){try{" +
            "var m=window.W&&W.map&&W.map.map;if(!m||!m.flyTo)return 'sin_api';" +
            "m.stop();" +
            "try{if(W.broadcast)W.broadcast.emit('rqstClose','picker');}catch(e){}" +
            // Al terminar el vuelo se redibujan las etiquetas de ciudades: la capa de Windy (labels-layer)
            // dejaba la del zoom anterior encima (p. ej. "Ciudad de Río Cuarto" sobre Alta Gracia)
            "m.once('moveend',function(){" +
            "try{m.eachLayer(function(l){var c=l.getContainer&&l.getContainer();if(c&&/labels-layer/.test(c.className)&&l.redraw)l.redraw();});}catch(e){}" +
            "try{if(W.broadcast)W.broadcast.emit('rqstOpen','picker',{lat:$lat,lon:$lon});}catch(e){}});" +
            "m.flyTo([$lat,$lon],${vista.zoom},{duration:$DURACION_VUELO_S,easeLinearity:0.25});" +
            "return 'ok';" +
            "}catch(e){return 'error';}})()"
    }
}
