import { afterEach, describe, expect, it, vi } from 'vitest'

import { getHomepagePromotions, listPromotionPlacements } from './promotions-api'

function jsonResponse(status: number, body: unknown) {
  return new Response(JSON.stringify(body), {
    status,
    headers: { 'Content-Type': 'application/json' },
  })
}

describe('promotions-api', () => {
  afterEach(() => {
    vi.unstubAllGlobals()
  })

  it('GET /promotions/homepage returns typed homepage payload', async () => {
    const fetchMock = vi.fn(async () =>
      jsonResponse(200, {
        hero: [],
        promoTop: null,
        promoBottom: null,
      }),
    )
    vi.stubGlobal('fetch', fetchMock)

    const result = await getHomepagePromotions()
    expect(result).toEqual({ hero: [], promoTop: null, promoBottom: null })
    const homepageUrl = String((fetchMock.mock.calls as unknown as [unknown[]][])[0]?.[0])
    expect(homepageUrl).toContain('/promotions/homepage')
  })

  it('GET /promotions/placements forwards comma-separated keys', async () => {
    const fetchMock = vi.fn(async () => jsonResponse(200, []))
    vi.stubGlobal('fetch', fetchMock)

    await listPromotionPlacements(['HOME_PROMO_BOTTOM', 'HOME_HERO'])
    const placementsUrl = String((fetchMock.mock.calls as unknown as [unknown[]][])[0]?.[0])
    expect(placementsUrl).toContain(
      '/promotions/placements?keys=HOME_PROMO_BOTTOM%2CHOME_HERO',
    )
  })
})
