export function money(value) {
  const number = Number(value ?? 0)
  return number.toFixed(2)
}

export function dateTime(value) {
  if (!value) return '—'
  return String(value).replace('T', ' ').slice(0, 19)
}

export function shortDate(value) {
  if (!value) return '—'
  return String(value).replace('T', ' ').slice(0, 16)
}

export const ORDER_STATUS = {
  0: { text: '待支付', tone: 'warning' },
  10: { text: '已支付', tone: 'info' },
  20: { text: '服务中', tone: 'primary' },
  30: { text: '已完成', tone: 'success' },
  40: { text: '已取消', tone: 'info' },
  50: { text: '退款中', tone: 'warning' },
  60: { text: '已退款', tone: 'info' }
}

export function orderStatus(status) {
  return ORDER_STATUS[status] || { text: '未知', tone: 'info' }
}

export const PAY_STATUS = {
  0: { text: '支付中', tone: 'warning' },
  1: { text: '支付成功', tone: 'success' },
  2: { text: '支付失败', tone: 'danger' },
  3: { text: '已关闭', tone: 'info' }
}

export function payStatus(status) {
  return PAY_STATUS[status] || { text: '未知', tone: 'info' }
}

export function maskUser(id) {
  const text = String(id ?? '')
  return text.length >= 4 ? `用户 ${text.slice(-4)}` : '匿名用户'
}

export function resolveImage(url) {
  if (!url) return '/images/product/placeholder.jpg'
  return url
}

const SHOP_IMAGES = [
  '/images/shop/shop-1.jpg',
  '/images/shop/shop-2.jpg',
  '/images/shop/shop-3.jpg'
]

const SERVICE_IMAGES = {
  保养: '/images/shop/service-maintenance.jpg',
  洗车: '/images/shop/service-wash.jpg',
  轮胎: '/images/shop/service-tire.jpg',
  检测: '/images/shop/service-inspection.jpg'
}

const SERVICE_KEYWORDS = [
  [/洗车|精洗|洗护/, '/images/shop/service-wash.jpg'],
  [/轮胎|轮毂|动平衡|四轮定位/, '/images/shop/service-tire.jpg'],
  [/空调|消毒|除味/, '/images/shop/service-ac.jpg'],
  [/雨刮|雨刷/, '/images/shop/service-wiper.jpg'],
  [/检测|验车|诊断/, '/images/shop/service-inspection.jpg'],
  [/机油|保养|换油|滤芯/, '/images/shop/service-maintenance.jpg']
]

export function resolveShopImage(shop) {
  if (shop?.logo) return shop.logo
  const id = Number(shop?.id)
  const index = Number.isFinite(id) && id > 0 ? (id - 1) % SHOP_IMAGES.length : 0
  return SHOP_IMAGES[index]
}

export function resolveServiceImage(service) {
  if (service?.coverImg) return service.coverImg
  const name = String(service?.itemName || '')
  for (const [pattern, image] of SERVICE_KEYWORDS) {
    if (pattern.test(name)) return image
  }
  return SERVICE_IMAGES[service?.category] || SHOP_IMAGES[1]
}

export function resolveOrderItemImage(order, item) {
  if (item?.itemImg) return item.itemImg
  if (Number(order?.orderType) === 2) {
    return resolveServiceImage({ itemName: item?.itemName })
  }
  return '/images/product/placeholder.jpg'
}

export function priceParts(value) {
  const [int, decimal] = Number(value ?? 0).toFixed(2).split('.')
  return { int, decimal }
}

export function normalizePage(page, fallbackSize = 10) {
  if (!page) return { records: [], total: 0, pageNum: 1, pageSize: fallbackSize }
  const records = page.records || []
  const total = Number(page.total ?? 0)
  return {
    records,
    total,
    pageNum: Number(page.current ?? page.pageNum ?? 1),
    pageSize: Number(page.size ?? page.pageSize ?? fallbackSize)
  }
}
