package com.seasaw.theta.core.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.dp

/**
 * Line icons from Lucide (https://lucide.dev, ISC licence; the notice is in Settings > About > Open-source licences),
 * converted to vectors by a build script. Only the icons the app uses are included, which keeps them to a few
 * kilobytes, where a full icon library would add megabytes.
 *
 * Drawn as strokes, not fills, for a light modern look. The stroke takes the Icon tint like any vector.
 */
@Suppress("LargeClass")
object ThetaIcons {
    val Back by lineIcon(
        "arrow-left",
        "m12 19-7-7 7-7",
        "M19 12H5",
    )

    val Forward by lineIcon(
        "arrow-right",
        "M5 12h14",
        "m12 5 7 7-7 7",
    )

    val Plus by lineIcon(
        "plus",
        "M5 12h14",
        "M12 5v14",
    )

    val Close by lineIcon(
        "x",
        "M18 6 6 18",
        "m6 6 12 12",
    )

    val More by lineIcon(
        "ellipsis-vertical",
        "M11,12a1,1 0 1,0 2,0a1,1 0 1,0 -2,0",
        "M11,5a1,1 0 1,0 2,0a1,1 0 1,0 -2,0",
        "M11,19a1,1 0 1,0 2,0a1,1 0 1,0 -2,0",
    )

    val Home by lineIcon(
        "house",
        "M15 21v-8a1 1 0 0 0-1-1h-4a1 1 0 0 0-1 1v8",
        "M3 10a2 2 0 0 1 .709-1.528l7-6a2 2 0 0 1 2.582 0l7 6A2 2 0 0 1 21 10v9a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2z",
    )

    val Bookmark by lineIcon(
        "bookmark",
        "M17 3a2 2 0 0 1 2 2v15a1 1 0 0 1-1.496.868l-4.512-2.578a2 2 0 0 0-1.984 0l-4.512 2.578A1 1 0 0 1 5 20V5a2 2 0 0 1 2-2z",
    )

    val Bookmarked by lineIcon(
        "bookmark-check",
        "M17 3a2 2 0 0 1 2 2v15a1 1 0 0 1-1.496.868l-4.512-2.578a2 2 0 0 0-1.984 0l-4.512 2.578A1 1 0 0 1 5 20V5a2 2 0 0 1 2-2z",
        "m9 10 2 2 4-4",
    )

    val Search by lineIcon(
        "search",
        "m21 21-4.34-4.34",
        "M3,11a8,8 0 1,0 16,0a8,8 0 1,0 -16,0",
    )

    val History by lineIcon(
        "history",
        "M3 12a9 9 0 1 0 9-9 9.75 9.75 0 0 0-6.74 2.74L3 8",
        "M3 3v5h5",
        "M12 7v5l4 2",
    )

    val Download by lineIcon(
        "download",
        "M12 15V3",
        "M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4",
        "m7 10 5 5 5-5",
    )

    val Settings by lineIcon(
        "settings",
        "M9.671 4.136a2.34 2.34 0 0 1 4.659 0 2.34 2.34 0 0 0 3.319 1.915 2.34 2.34 0 0 1 2.33 4.033 2.34 2.34 0 0 0 0 3.831 2.34 2.34 0 0 1-2.33 4.033 2.34 2.34 0 0 0-3.319 1.915 2.34 2.34 0 0 1-4.659 0 2.34 2.34 0 0 0-3.32-1.915 2.34 2.34 0 0 1-2.33-4.033 2.34 2.34 0 0 0 0-3.831A2.34 2.34 0 0 1 6.35 6.051a2.34 2.34 0 0 0 3.319-1.915",
        "M9,12a3,3 0 1,0 6,0a3,3 0 1,0 -6,0",
    )

    val Shield by lineIcon(
        "shield",
        "M20 13c0 5-3.5 7.5-7.66 8.95a1 1 0 0 1-.67-.01C7.5 20.5 4 18 4 13V6a1 1 0 0 1 1-1c2 0 4.5-1.2 6.24-2.72a1.17 1.17 0 0 1 1.52 0C14.51 3.81 17 5 19 5a1 1 0 0 1 1 1z",
    )

    val ShieldCheck by lineIcon(
        "shield-check",
        "M20 13c0 5-3.5 7.5-7.66 8.95a1 1 0 0 1-.67-.01C7.5 20.5 4 18 4 13V6a1 1 0 0 1 1-1c2 0 4.5-1.2 6.24-2.72a1.17 1.17 0 0 1 1.52 0C14.51 3.81 17 5 19 5a1 1 0 0 1 1 1z",
        "m9 12 2 2 4-4",
    )

    val ShieldOff by lineIcon(
        "shield-off",
        "m2 2 20 20",
        "M5 5a1 1 0 0 0-1 1v7c0 5 3.5 7.5 7.67 8.94a1 1 0 0 0 .67.01c2.35-.82 4.48-1.97 5.9-3.71",
        "M9.309 3.652A12.252 12.252 0 0 0 11.24 2.28a1.17 1.17 0 0 1 1.52 0C14.51 3.81 17 5 19 5a1 1 0 0 1 1 1v7a9.784 9.784 0 0 1-.08 1.264",
    )

    val Lock by lineIcon(
        "lock",
        "M5,11h14a2,2 0 0 1 2,2v7a2,2 0 0 1 -2,2h-14a2,2 0 0 1 -2,-2v-7a2,2 0 0 1 2,-2Z",
        "M7 11V7a5 5 0 0 1 10 0v4",
    )

    val Warning by lineIcon(
        "triangle-alert",
        "m21.73 18-8-14a2 2 0 0 0-3.48 0l-8 14A2 2 0 0 0 4 21h16a2 2 0 0 0 1.73-3",
        "M12 9v4",
        "M12 17h.01",
    )

    val Reload by lineIcon(
        "rotate-cw",
        "M21 12a9 9 0 1 1-9-9c2.52 0 4.93 1 6.74 2.74L21 8",
        "M21 3v5h-5",
    )

    val ChevronDown by lineIcon(
        "chevron-down",
        "m6 9 6 6 6-6",
    )

    val ChevronUp by lineIcon(
        "chevron-up",
        "m18 15-6-6-6 6",
    )

    val ChevronRight by lineIcon(
        "chevron-right",
        "m9 18 6-6-6-6",
    )

    val Groups by lineIcon(
        "layout-grid",
        "M4,3h5a1,1 0 0 1 1,1v5a1,1 0 0 1 -1,1h-5a1,1 0 0 1 -1,-1v-5a1,1 0 0 1 1,-1Z",
        "M15,3h5a1,1 0 0 1 1,1v5a1,1 0 0 1 -1,1h-5a1,1 0 0 1 -1,-1v-5a1,1 0 0 1 1,-1Z",
        "M15,14h5a1,1 0 0 1 1,1v5a1,1 0 0 1 -1,1h-5a1,1 0 0 1 -1,-1v-5a1,1 0 0 1 1,-1Z",
        "M4,14h5a1,1 0 0 1 1,1v5a1,1 0 0 1 -1,1h-5a1,1 0 0 1 -1,-1v-5a1,1 0 0 1 1,-1Z",
    )

    val NewGroup by lineIcon(
        "grid-2x2-plus",
        "M12 3v17a1 1 0 0 1-1 1H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h14a2 2 0 0 1 2 2v6a1 1 0 0 1-1 1H3",
        "M16 19h6",
        "M19 22v-6",
    )

    val Private by lineIcon(
        "glasses",
        "M2,15a4,4 0 1,0 8,0a4,4 0 1,0 -8,0",
        "M14,15a4,4 0 1,0 8,0a4,4 0 1,0 -8,0",
        "M14 15a2 2 0 0 0-2-2 2 2 0 0 0-2 2",
        "M2.5 13 5 7c.7-1.3 1.4-2 3-2",
        "M21.5 13 19 7c-.7-1.3-1.5-2-3-2",
    )

    val Eye by lineIcon(
        "eye",
        "M2.062 12.348a1 1 0 0 1 0-.696 10.75 10.75 0 0 1 19.876 0 1 1 0 0 1 0 .696 10.75 10.75 0 0 1-19.876 0",
        "M9,12a3,3 0 1,0 6,0a3,3 0 1,0 -6,0",
    )

    val EyeOff by lineIcon(
        "eye-off",
        "M10.733 5.076a10.744 10.744 0 0 1 11.205 6.575 1 1 0 0 1 0 .696 10.747 10.747 0 0 1-1.444 2.49",
        "M14.084 14.158a3 3 0 0 1-4.242-4.242",
        "M17.479 17.499a10.75 10.75 0 0 1-15.417-5.151 1 1 0 0 1 0-.696 10.75 10.75 0 0 1 4.446-5.143",
        "m2 2 20 20",
    )

    val Check by lineIcon(
        "check",
        "M20 6 9 17l-5-5",
    )

    val CheckCircle by lineIcon(
        "circle-check",
        "M2,12a10,10 0 1,0 20,0a10,10 0 1,0 -20,0",
        "m16 9-5.5 5.5L8 12",
    )

    val Circle by lineIcon(
        "circle",
        "M2,12a10,10 0 1,0 20,0a10,10 0 1,0 -20,0",
    )

    val Trash by lineIcon(
        "trash-2",
        "M10 11v6",
        "M14 11v6",
        "M19 6v14a2 2 0 0 1-2 2H7a2 2 0 0 1-2-2V6",
        "M3 6h18",
        "M8 6V4a2 2 0 0 1 2-2h4a2 2 0 0 1 2 2v2",
    )

    val Share by lineIcon(
        "share-2",
        "M15,5a3,3 0 1,0 6,0a3,3 0 1,0 -6,0",
        "M3,12a3,3 0 1,0 6,0a3,3 0 1,0 -6,0",
        "M15,19a3,3 0 1,0 6,0a3,3 0 1,0 -6,0",
        "M8.59,13.51L15.42,17.49",
        "M15.41,6.51L8.59,10.49",
    )

    val Desktop by lineIcon(
        "monitor",
        "M4,3h16a2,2 0 0 1 2,2v10a2,2 0 0 1 -2,2h-16a2,2 0 0 1 -2,-2v-10a2,2 0 0 1 2,-2Z",
        "M8,21L16,21",
        "M12,17L12,21",
    )

    val Lite by lineIcon(
        "zap",
        "M15.914 4a1.5 1.5 0 00-2.474-1.561l-9 9A1.5 1.5 0 005.5 14h4.002a.5.5 0 01.471.666L8.086 20a1.5 1.5 0 002.475 1.56l9-9A1.5 1.5 0 0018.5 10h-3.997a.5.5 0 01-.472-.667z",
    )

    val TextSize by lineIcon(
        "type",
        "M12 4v16",
        "M4 7V5a1 1 0 0 1 1-1h14a1 1 0 0 1 1 1v2",
        "M9 20h6",
    )

    val Languages by lineIcon(
        "languages",
        "m5 8 6 6",
        "m4 14 6-6 2-3",
        "M2 5h12",
        "M7 2h1",
        "m22 22-5-10-5 10",
        "M14 18h6",
    )

    val Palette by lineIcon(
        "palette",
        "M12 22a1 1 0 0 1 0-20 10 9 0 0 1 10 9 5 5 0 0 1-5 5h-2.25a1.75 1.75 0 0 0-1.4 2.8l.3.4a1.75 1.75 0 0 1-1.4 2.8z",
        "M13,6.5a0.5,0.5 0 1,0 1,0a0.5,0.5 0 1,0 -1,0",
        "M17,10.5a0.5,0.5 0 1,0 1,0a0.5,0.5 0 1,0 -1,0",
        "M6,12.5a0.5,0.5 0 1,0 1,0a0.5,0.5 0 1,0 -1,0",
        "M8,7.5a0.5,0.5 0 1,0 1,0a0.5,0.5 0 1,0 -1,0",
    )

    val Accessibility by lineIcon(
        "person-standing",
        "M11,5a1,1 0 1,0 2,0a1,1 0 1,0 -2,0",
        "m9 20 3-6 3 6",
        "m6 8 6 2 6-2",
        "M12 10v4",
    )

    val SiteSettings by lineIcon(
        "sliders-horizontal",
        "M10 5H3",
        "M12 19H3",
        "M14 3v4",
        "M16 17v4",
        "M21 12h-9",
        "M21 19h-5",
        "M21 5h-7",
        "M8 10v4",
        "M8 12H3",
    )

    val Info by lineIcon(
        "info",
        "M2,12a10,10 0 1,0 20,0a10,10 0 1,0 -20,0",
        "M12 16v-4",
        "M12 8h.01",
    )

    val Star by lineIcon(
        "star",
        "M11.525 2.295a.53.53 0 0 1 .95 0l2.31 4.679a2.123 2.123 0 0 0 1.595 1.16l5.166.756a.53.53 0 0 1 .294.904l-3.736 3.638a2.123 2.123 0 0 0-.611 1.878l.882 5.14a.53.53 0 0 1-.771.56l-4.618-2.428a2.122 2.122 0 0 0-1.973 0L6.396 21.01a.53.53 0 0 1-.77-.56l.881-5.139a2.122 2.122 0 0 0-.611-1.879L2.16 9.795a.53.53 0 0 1 .294-.906l5.165-.755a2.122 2.122 0 0 0 1.597-1.16z",
    )

    val Globe by lineIcon(
        "globe",
        "M2,12a10,10 0 1,0 20,0a10,10 0 1,0 -20,0",
        "M12 2a14.5 14.5 0 0 0 0 20 14.5 14.5 0 0 0 0-20",
        "M2 12h20",
    )

    val Select by lineIcon(
        "list-checks",
        "M13 5h8",
        "M13 12h8",
        "M13 19h8",
        "m3 17 2 2 4-4",
        "m3 7 2 2 4-4",
    )

    val Edit by lineIcon(
        "pencil",
        "M21.174 6.812a1 1 0 0 0-3.986-3.987L3.842 16.174a2 2 0 0 0-.5.83l-1.321 4.352a.5.5 0 0 0 .623.622l4.353-1.32a2 2 0 0 0 .83-.497z",
        "m15 5 4 4",
    )

    val Ungroup by lineIcon(
        "ungroup",
        "M13,14h6a2,2 0 0 1 2,2v3a2,2 0 0 1 -2,2h-6a2,2 0 0 1 -2,-2v-3a2,2 0 0 1 2,-2Z",
        "M5,3h6a2,2 0 0 1 2,2v3a2,2 0 0 1 -2,2h-6a2,2 0 0 1 -2,-2v-3a2,2 0 0 1 2,-2Z",
    )

    val Image by lineIcon(
        "image",
        "M5,3h14a2,2 0 0 1 2,2v14a2,2 0 0 1 -2,2h-14a2,2 0 0 1 -2,-2v-14a2,2 0 0 1 2,-2Z",
        "M7,9a2,2 0 1,0 4,0a2,2 0 1,0 -4,0",
        "m21 15-3.086-3.086a2 2 0 0 0-2.828 0L6 21",
    )

    val Stats by lineIcon(
        "chart-column",
        "M3 3v16a2 2 0 0 0 2 2h16",
        "M18 17V9",
        "M13 17V5",
        "M8 17v-3",
    )

    val Contrast by lineIcon(
        "contrast",
        "M2,12a10,10 0 1,0 20,0a10,10 0 1,0 -20,0",
        "M12 18a6 6 0 0 0 0-12v12z",
    )

    val ZoomIn by lineIcon(
        "zoom-in",
        "M3,11a8,8 0 1,0 16,0a8,8 0 1,0 -16,0",
        "M21,21L16.65,16.65",
        "M11,8L11,14",
        "M8,11L14,11",
    )

    val ExternalLink by lineIcon(
        "external-link",
        "M15 3h6v6",
        "M10 14 21 3",
        "M18 13v6a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V8a2 2 0 0 1 2-2h6",
    )

    val Link by lineIcon(
        "link",
        "M10 13a5 5 0 0 0 7.54.54l3-3a5 5 0 0 0-7.07-7.07l-1.72 1.71",
        "M14 11a5 5 0 0 0-7.54-.54l-3 3a5 5 0 0 0 7.07 7.07l1.71-1.71",
    )

    val Copy by lineIcon(
        "copy",
        "M10,8h10a2,2 0 0 1 2,2v10a2,2 0 0 1 -2,2h-10a2,2 0 0 1 -2,-2v-10a2,2 0 0 1 2,-2Z",
        "M4 16c-1.1 0-2-.9-2-2V4c0-1.1.9-2 2-2h10c1.1 0 2 .9 2 2",
    )

    val Tabs by lineIcon(
        "layers",
        "M12.83 2.18a2 2 0 0 0-1.66 0L2.6 6.08a1 1 0 0 0 0 1.83l8.58 3.91a2 2 0 0 0 1.66 0l8.58-3.9a1 1 0 0 0 0-1.83z",
        "M2 12a1 1 0 0 0 .58.91l8.6 3.91a2 2 0 0 0 1.65 0l8.58-3.9A1 1 0 0 0 22 12",
        "M2 17a1 1 0 0 0 .58.91l8.6 3.91a2 2 0 0 0 1.65 0l8.58-3.9A1 1 0 0 0 22 17",
    )

    val Camera by lineIcon(
        "camera",
        "M13.997 4a2 2 0 0 1 1.76 1.05l.486.9A2 2 0 0 0 18.003 7H20a2 2 0 0 1 2 2v9a2 2 0 0 1-2 2H4a2 2 0 0 1-2-2V9a2 2 0 0 1 2-2h1.997a2 2 0 0 0 1.759-1.048l.489-.904A2 2 0 0 1 10.004 4z",
        "M9,13a3,3 0 1,0 6,0a3,3 0 1,0 -6,0",
    )

    val Mic by lineIcon(
        "mic",
        "M12 19v3",
        "M19 10v2a7 7 0 0 1-14 0v-2",
        "M12,2h0a3,3 0 0 1 3,3v7a3,3 0 0 1 -3,3h-0a3,3 0 0 1 -3,-3v-7a3,3 0 0 1 3,-3Z",
    )

    val Location by lineIcon(
        "map-pin",
        "M20 10c0 4.993-5.539 10.193-7.399 11.799a1 1 0 0 1-1.202 0C9.539 20.193 4 14.993 4 10a8 8 0 0 1 16 0",
        "M9,10a3,3 0 1,0 6,0a3,3 0 1,0 -6,0",
    )

    val Smartphone by lineIcon(
        "smartphone",
        "M7,2h10a2,2 0 0 1 2,2v16a2,2 0 0 1 -2,2h-10a2,2 0 0 1 -2,-2v-16a2,2 0 0 1 2,-2Z",
        "M12 18h.01",
    )

    val Theme by lineIcon(
        "sun-moon",
        "M12 2v2",
        "M14.837 16.385a6 6 0 1 1-7.223-7.222c.624-.147.97.66.715 1.248a4 4 0 0 0 5.26 5.259c.589-.255 1.396.09 1.248.715",
        "M16 12a4 4 0 0 0-4-4",
        "m19 5-1.256 1.256",
        "M20 12h2",
    )

    val Licence by lineIcon(
        "scroll-text",
        "M15 12h-5",
        "M15 8h-5",
        "M19 17V5a2 2 0 0 0-2-2H4",
        "M8 21h12a2 2 0 0 0 2-2v-1a1 1 0 0 0-1-1H11a1 1 0 0 0-1 1v1a2 2 0 1 1-4 0V5a2 2 0 1 0-4 0v2a1 1 0 0 0 1 1h3",
    )

    val Film by lineIcon(
        "film",
        "M5,3h14a2,2 0 0 1 2,2v14a2,2 0 0 1 -2,2h-14a2,2 0 0 1 -2,-2v-14a2,2 0 0 1 2,-2Z",
        "M7 3v18",
        "M3 7.5h4",
        "M3 12h18",
        "M3 16.5h4",
        "M17 3v18",
        "M17 7.5h4",
        "M17 16.5h4",
    )

    val Timer by lineIcon(
        "timer",
        "M10,2L14,2",
        "M12,14L15,11",
        "M4,14a8,8 0 1,0 16,0a8,8 0 1,0 -16,0",
    )

    val DataSaved by lineIcon(
        "gauge",
        "m12 14 4-4",
        "M3.34 19a10 10 0 1 1 17.32 0",
    )

    val Cookie by lineIcon(
        "cookie",
        "M11 17h.01",
        "M11.496 2c.324-.016.558.292.529.615a4 4 0 004.235 4.368.713.713 0 01.758.757 4 4 0 004.366 4.237c.323-.03.63.204.614.527a10 10 0 01-2.915 6.566A1 1 0 114.93 4.918 10 10 0 0111.496 2",
        "M12 12h.01",
        "M16 16h.01",
        "M16 3h.01",
        "M21 4h.01",
        "M21 8h.01",
        "M7 14h.01",
        "M9 8h.01",
    )

    val Motion by lineIcon(
        "wind",
        "M12.8 19.6A2 2 0 1 0 14 16H2",
        "M17.5 8a2.5 2.5 0 1 1 2 4H2",
        "M9.8 4.4A2 2 0 1 1 11 8H2",
    )

    val Clock by lineIcon(
        "clock",
        "M2,12a10,10 0 1,0 20,0a10,10 0 1,0 -20,0",
        "M12 6v6l4 2",
    )

    val Find by lineIcon(
        "text-search",
        "M21 5H3",
        "M10 12H3",
        "M10 19H3",
        "M14,15a3,3 0 1,0 6,0a3,3 0 1,0 -6,0",
        "m21 19-1.9-1.9",
    )

    val NewPrivateTab by lineIcon(
        "glasses",
        "M2,15a4,4 0 1,0 8,0a4,4 0 1,0 -8,0",
        "M14,15a4,4 0 1,0 8,0a4,4 0 1,0 -8,0",
        "M14 15a2 2 0 0 0-2-2 2 2 0 0 0-2 2",
        "M2.5 13 5 7c.7-1.3 1.4-2 3-2",
        "M21.5 13 19 7c-.7-1.3-1.5-2-3-2",
    )

    val FolderOpen by lineIcon(
        "folder-open",
        "m6 14 1.5-2.9A2 2 0 0 1 9.24 10H20a2 2 0 0 1 1.94 2.5l-1.54 6a2 2 0 0 1-1.95 1.5H4a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h3.9a2 2 0 0 1 1.69.9l.81 1.2a2 2 0 0 0 1.67.9H18a2 2 0 0 1 2 2v2",
    )

    val Hand by lineIcon(
        "hand",
        "M18 11V6a2 2 0 0 0-2-2a2 2 0 0 0-2 2",
        "M14 10V4a2 2 0 0 0-2-2a2 2 0 0 0-2 2v2",
        "M10 10.5V6a2 2 0 0 0-2-2a2 2 0 0 0-2 2v8",
        "M18 8a2 2 0 1 1 4 0v6a8 8 0 0 1-8 8h-2c-2.8 0-4.5-.86-5.99-2.34l-3.6-3.6a2 2 0 0 1 2.83-2.82L7 15",
    )

    private fun lineIcon(name: String, vararg paths: String) = lazy {
        ImageVector.Builder(
            name = name,
            defaultWidth = ICON_SIZE.dp,
            defaultHeight = ICON_SIZE.dp,
            viewportWidth = ICON_SIZE,
            viewportHeight = ICON_SIZE,
        ).apply {
            paths.forEach { path ->
                addPath(
                    pathData = addPathNodes(path),
                    fill = null,
                    stroke = SolidColor(Color.Black),
                    strokeLineWidth = STROKE_WIDTH,
                    strokeLineCap = StrokeCap.Round,
                    strokeLineJoin = StrokeJoin.Round,
                )
            }
        }.build()
    }

    private const val ICON_SIZE = 24f

    /** Lucide draws at 2; a little lighter reads as more refined at phone sizes. */
    private const val STROKE_WIDTH = 1.75f
}
