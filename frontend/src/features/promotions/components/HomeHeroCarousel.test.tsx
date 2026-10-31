import { fireEvent, render, screen } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import { describe, expect, it, vi } from 'vitest'

import type { PublicPlacement } from '../types'
import { HomeHeroCarousel } from './HomeHeroCarousel'

vi.mock('@/hooks/use-media-query', () => ({
  useMediaQuery: () => false,
}))

function slide(overrides: Partial<PublicPlacement> = {}): PublicPlacement {
  return {
    id: '11111111-1111-4111-8111-111111111111',
    campaignId: '22222222-2222-4222-8222-222222222222',
    placementKey: 'HOME_HERO',
    title: 'Ưu đãi mua sắm tiết kiệm',
    subtitle: 'Chọn thực phẩm tươi mỗi ngày',
    ctaLabel: null,
    cta: null,
    desktopImageUrl: '/uploads/promotions/bbbbbbbb-cccc-4ddd-8eee-ffffffffffff.jpg',
    mobileImageUrl: null,
    displayOrder: 1,
    ...overrides,
  }
}

describe('HomeHeroCarousel', () => {
  it('renders campaign artwork when image URL resolves', () => {
    render(
      <MemoryRouter>
        <HomeHeroCarousel slides={[slide()]} />
      </MemoryRouter>,
    )

    const img = document.querySelector('img')
    expect(img).toHaveAttribute(
      'src',
      '/uploads/promotions/bbbbbbbb-cccc-4ddd-8eee-ffffffffffff.jpg',
    )
    expect(screen.getByText('Ưu đãi mua sắm tiết kiệm')).toBeInTheDocument()
  })

  it('falls back to title overlay without broken-image icon when load fails', () => {
    render(
      <MemoryRouter>
        <HomeHeroCarousel slides={[slide()]} />
      </MemoryRouter>,
    )

    const img = document.querySelector('img')
    expect(img).not.toBeNull()
    fireEvent.error(img!)

    expect(document.querySelector('img')).toBeNull()
    expect(screen.getByText('Ưu đãi mua sắm tiết kiệm')).toBeInTheDocument()
    expect(screen.getByText('Chọn thực phẩm tươi mỗi ngày')).toBeInTheDocument()
  })

  it('shows empty state when no resolvable image URLs', () => {
    render(
      <MemoryRouter>
        <HomeHeroCarousel
          slides={[slide({ desktopImageUrl: 'javascript:alert(1)', mobileImageUrl: null })]}
        />
      </MemoryRouter>,
    )

    expect(screen.getByRole('status')).toHaveTextContent(/no hero creatives available/i)
  })
})
