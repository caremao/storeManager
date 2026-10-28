// Clases compartidas, tomadas del estilo del proyecto original.

export const card = 'bg-gray-800 border border-gray-700 rounded-lg shadow-md'

export const input =
  'w-full px-3 py-2 bg-gray-700 text-gray-100 placeholder-gray-400 border border-gray-600 rounded-lg ' +
  'focus:outline-none focus:ring-2 focus:ring-indigo-500 focus:ring-offset-gray-800'

export const label = 'block text-sm font-medium text-gray-300 mb-1'

const button =
  'inline-flex items-center justify-center gap-2 rounded-lg font-medium transition-colors ' +
  'focus:outline-none focus:ring-2 focus:ring-offset-2 focus:ring-offset-gray-900 disabled:opacity-50 disabled:cursor-not-allowed'

export const primaryButton = `${button} px-4 py-2 bg-indigo-600 text-white hover:bg-indigo-700 focus:ring-indigo-500`
export const secondaryButton = `${button} px-4 py-2 text-gray-200 border border-gray-600 hover:bg-gray-700 focus:ring-gray-500`
export const dangerButton = `${button} px-4 py-2 text-red-400 border border-red-500 hover:bg-red-900/30 focus:ring-red-500`
export const successButton = `${button} px-4 py-2 bg-green-600 text-white hover:bg-green-700 focus:ring-green-500`
export const dangerSolidButton = `${button} px-4 py-2 bg-red-600 text-white hover:bg-red-700 focus:ring-red-500`
export const smallButton = 'px-3 py-1.5 text-sm'
