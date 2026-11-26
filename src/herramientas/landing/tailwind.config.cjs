// Paleta institucional GRANEX: tema "Verde Campo" de la app (ui/theme/Paletas.kt). Genera web/css/tailwind.css:
// ver herramientas/landing/LEEME.md
module.exports = {
  content: ["web/*.html", "web/js/*.js"],
  theme: {
    extend: {
      colors: {
        agro: {
          50: '#ECFDF5',
          100: '#D1FAE5',
          200: '#A7F3D0',
          400: '#34D399',
          500: '#10B981',
          600: '#059669',
          700: '#047857', // primario de la marca
          800: '#065F46',
          900: '#064E3B',
          950: '#061A14'
        },
        trigo: {
          300: '#FCD34D',
          400: '#FBBF24',
          600: '#D97706', // acento
          700: '#B45309'
        },
        cielo: '#0284C7',
        campo: '#F7F9F2', // fondo
        pasto: '#EFF5EB', // superficie variante
        tinta: '#0F172A',
        gris: '#64748B'
      },
      fontFamily: {
        sans: ['Inter', 'system-ui', 'sans-serif'],
        display: ['Manrope', 'Inter', 'system-ui', 'sans-serif']
      },
      boxShadow: {
        suave: '0 1px 2px rgba(6,78,59,.06), 0 8px 24px -8px rgba(6,78,59,.14)',
        telefono: '0 40px 80px -24px rgba(6,78,59,.45), 0 12px 24px -12px rgba(15,23,42,.25)'
      }
    }
  }
};
