import { zodResolver } from '@hookform/resolvers/zod'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useEffect, useMemo, useState } from 'react'
import { useForm } from 'react-hook-form'
import { useTranslation } from 'react-i18next'

import { Input } from '@/components/ui/input'
import { ApiClientError } from '@/lib/api/api-error'

import {
  getPlacementTranslations,
  putPlacementTranslations,
} from '../api/admin-translations-api'
import { adminTranslationQueryKeys } from '../hooks/admin-translation-query-keys'
import { invalidateAfterTranslationSave } from '../hooks/invalidate-after-translation-save'
import {
  createPlacementTranslationsSchema,
  type PlacementTranslationsFormValues,
} from '../schemas'
import type { PlacementTranslationsPayload, PlacementTranslationsResponse } from '../types'
import {
  TranslationField,
  TranslationLocaleGroup,
  TranslationsEditorFrame,
} from './TranslationsEditorFrame'

type FormInput = {
  viTitle: string
  viSubtitle: string
  viCtaLabel: string
  enTitle: string
  enSubtitle: string
  enCtaLabel: string
}

function toFormValues(data: PlacementTranslationsResponse): FormInput {
  const { vi, en } = data.translations
  return {
    viTitle: vi?.title ?? '',
    viSubtitle: vi?.subtitle ?? '',
    viCtaLabel: vi?.ctaLabel ?? '',
    enTitle: en?.title ?? '',
    enSubtitle: en?.subtitle ?? '',
    enCtaLabel: en?.ctaLabel ?? '',
  }
}

function toPayload(values: PlacementTranslationsFormValues): PlacementTranslationsPayload {
  const optional = (value: string) => (value.trim() ? value.trim() : null)
  const payload: PlacementTranslationsPayload = {
    translations: {
      vi: {
        title: values.viTitle.trim(),
        subtitle: optional(values.viSubtitle),
        ctaLabel: optional(values.viCtaLabel),
      },
    },
  }
  if (values.enTitle.trim()) {
    payload.translations.en = {
      title: values.enTitle.trim(),
      subtitle: optional(values.enSubtitle),
      ctaLabel: optional(values.enCtaLabel),
    }
  }
  return payload
}

type Props = {
  placementId: string
}

/** VI + EN title / subtitle / CTA label editor for an existing campaign placement. */
export function PlacementTranslationsEditor({ placementId }: Props) {
  const { t } = useTranslation('admin')
  const queryClient = useQueryClient()
  const [saved, setSaved] = useState(false)
  const [saveError, setSaveError] = useState<string | null>(null)

  const queryKey = adminTranslationQueryKeys.placement(placementId)
  const query = useQuery({
    queryKey,
    queryFn: ({ signal }) => getPlacementTranslations(placementId, signal),
    retry: false,
  })

  const resolver = useMemo(() => zodResolver(createPlacementTranslationsSchema(t)), [t])
  const form = useForm<FormInput, unknown, PlacementTranslationsFormValues>({
    resolver,
    defaultValues: {
      viTitle: '',
      viSubtitle: '',
      viCtaLabel: '',
      enTitle: '',
      enSubtitle: '',
      enCtaLabel: '',
    },
  })

  useEffect(() => {
    if (query.data) {
      form.reset(toFormValues(query.data))
    }
  }, [query.data, form])

  const mutation = useMutation({
    mutationFn: (values: PlacementTranslationsFormValues) =>
      putPlacementTranslations(placementId, toPayload(values)),
    onSuccess: async (data) => {
      setSaveError(null)
      setSaved(true)
      queryClient.setQueryData(queryKey, data)
      form.reset(toFormValues(data))
      await invalidateAfterTranslationSave(queryClient, 'promotion')
    },
    onError: (error) => {
      setSaved(false)
      setSaveError(
        error instanceof ApiClientError ? error.userMessage : t('translations.saveError'),
      )
    },
  })

  const errors = form.formState.errors
  const busy = mutation.isPending || form.formState.isSubmitting
  const prefix = `placement-${placementId}-translation`
  const labels = {
    title: t('translations.fieldTitle'),
    subtitle: t('translations.fieldSubtitle'),
    cta: t('translations.fieldCtaLabel'),
  }
  const vi = t('translations.viSuffix')
  const en = t('translations.enSuffix')

  return (
    <TranslationsEditorFrame
      title={t('translations.placementTitle')}
      description={t('translations.placementDescription')}
      testId={`placement-translations-editor-${placementId}`}
      isLoading={query.isLoading}
      loadError={
        query.isError
          ? query.error instanceof ApiClientError
            ? query.error.userMessage
            : t('translations.loadError')
          : null
      }
      onReload={() => void query.refetch()}
      saving={busy}
      saveError={saveError}
      saved={saved}
      onSubmit={(event) => {
        void form.handleSubmit((values) => {
          setSaved(false)
          setSaveError(null)
          mutation.mutate(values)
        })(event)
      }}
    >
      <TranslationLocaleGroup legend={t('translations.localeVi')}>
        <TranslationField
          id={`${prefix}-vi-title`}
          label={`${labels.title} (${vi})`}
          error={errors.viTitle?.message}
        >
          <Input
            id={`${prefix}-vi-title`}
            disabled={busy}
            aria-invalid={Boolean(errors.viTitle)}
            {...form.register('viTitle')}
          />
        </TranslationField>
        <TranslationField
          id={`${prefix}-vi-subtitle`}
          label={`${labels.subtitle} (${vi})`}
          error={errors.viSubtitle?.message}
        >
          <Input
            id={`${prefix}-vi-subtitle`}
            disabled={busy}
            aria-invalid={Boolean(errors.viSubtitle)}
            {...form.register('viSubtitle')}
          />
        </TranslationField>
        <TranslationField
          id={`${prefix}-vi-cta`}
          label={`${labels.cta} (${vi})`}
          error={errors.viCtaLabel?.message}
        >
          <Input
            id={`${prefix}-vi-cta`}
            disabled={busy}
            aria-invalid={Boolean(errors.viCtaLabel)}
            {...form.register('viCtaLabel')}
          />
        </TranslationField>
      </TranslationLocaleGroup>
      <TranslationLocaleGroup legend={t('translations.localeEn')}>
        <TranslationField
          id={`${prefix}-en-title`}
          label={`${labels.title} (${en})`}
          error={errors.enTitle?.message}
        >
          <Input
            id={`${prefix}-en-title`}
            disabled={busy}
            aria-invalid={Boolean(errors.enTitle)}
            {...form.register('enTitle')}
          />
        </TranslationField>
        <TranslationField
          id={`${prefix}-en-subtitle`}
          label={`${labels.subtitle} (${en})`}
          error={errors.enSubtitle?.message}
        >
          <Input
            id={`${prefix}-en-subtitle`}
            disabled={busy}
            aria-invalid={Boolean(errors.enSubtitle)}
            {...form.register('enSubtitle')}
          />
        </TranslationField>
        <TranslationField
          id={`${prefix}-en-cta`}
          label={`${labels.cta} (${en})`}
          error={errors.enCtaLabel?.message}
        >
          <Input
            id={`${prefix}-en-cta`}
            disabled={busy}
            aria-invalid={Boolean(errors.enCtaLabel)}
            {...form.register('enCtaLabel')}
          />
        </TranslationField>
      </TranslationLocaleGroup>
    </TranslationsEditorFrame>
  )
}
