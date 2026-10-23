import { createRequire } from 'node:module'
import { writeFile } from 'node:fs/promises'
import { resolve } from 'node:path'

const require = createRequire(resolve('Platfrom/ui/package.json'))
const React = require('react')
const { renderToStaticMarkup } = require('react-dom/server')
const names = {
  hub: 'HubOutlined', home: 'SpaceDashboardOutlined', deployments: 'DnsOutlined',
  apps: 'WidgetsOutlined', knowledge: 'DatasetOutlined', connections: 'CableOutlined',
  support: 'SupportAgentOutlined', reviews: 'FactCheckOutlined', behavior: 'AccountTreeOutlined',
  activity: 'HistoryOutlined', team: 'PeopleOutline', down: 'ExpandMoreRounded',
  right: 'ArrowForwardRounded', chevron: 'ChevronRightRounded', close: 'CloseRounded',
  expand: 'OpenInFullRounded', add: 'AddRounded', search: 'SearchRounded',
  more: 'MoreHorizRounded', external: 'OpenInNewRounded', warning: 'WarningAmberRounded',
  error: 'ErrorOutlineRounded', check: 'CheckCircleOutlineRounded', circle: 'RadioButtonUncheckedRounded',
  globe: 'LanguageOutlined', lock: 'LockOutlined', refresh: 'RefreshRounded',
  file: 'DescriptionOutlined', storage: 'StorageOutlined', bolt: 'BoltOutlined',
  bell: 'NotificationsNoneOutlined', menu: 'MenuRounded', copy: 'ContentCopyRounded',
  clock: 'ScheduleOutlined', palette: 'PaletteOutlined', code: 'CodeRounded',
  car: 'DirectionsCarOutlined', arrowback: 'ArrowBackRounded', grid: 'GridViewRounded',
  list: 'ViewListOutlined', play: 'PlayArrowRounded', shield: 'VerifiedUserOutlined',
  mail: 'MailOutlineRounded', settings: 'SettingsOutlined', help: 'HelpOutlineRounded',
}
const markup = Object.fromEntries(Object.entries(names).map(([key, name]) => {
  const Icon = require(`@mui/icons-material/${name}`).default
  return [key, renderToStaticMarkup(React.createElement(Icon))]
}))
await writeFile(new URL('icons.generated.js', import.meta.url),
  `/* Generated from the existing Platform MUI icon library. */\nwindow.PREVIEW_ICONS = ${JSON.stringify(markup)};\n`)
console.log(`Generated ${Object.keys(markup).length} existing-library icons; no external requests.`)
