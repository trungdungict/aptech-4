import { zodResolver } from '@hookform/resolvers/zod'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useEffect, useMemo, useState } from 'react'
import { useForm } from 'react-hook-form'
import { useTranslation } from 'react-i18next'

import { Input } from '@/components/ui/input'
import { ApiClientError } from '@/lib/api/api-error'

import { getNameTranslations, putNameTranslations } from '../api/admin-translations-api'
import { adminTranslationQueryKeys } from '../hooks/admin-translation-query-keys'
import { invalidateAfterTranslationSave } from '../hooks/invalidate-after-translation-save'
import { createNameTranslationsSchema, type NameTranslationsFormValues } from '../schemas'
import type {
  NameTranslationKind,
  NameTranslationsPayload,
  NameTranslationsResponse,
} from '../types'
import {
  TranslationField,
  TranslationLocaleGroup,
  TranslationsEditorFrame,
} from './TranslationsEditorFrame'

type FormInput = { viName: string; enName: string }

const NAME_MAX: Record<NameTranslationKind, number> = { strategy: 120, campaign: 160 }

function toFormValues(data: NameTranslationsResponse): FormInput {
  return { viName: data.translations.vi ?? '', enName: data.translations.en ?? '' }
}

function toPayload(values: NameTranslationsFormValues): NameTranslationsPayload {
  const payload: NameTranslationsPayload = { translations: { vi: values.viName.trim() } }
  if (values.enName.trim()) {
    payload.translations.en = values.enName.trim()
  }
  return payload
}

type Props = {
  kind: NameTranslationKind
  entityId: string
}

/** VI + EN display-name editor for an existing promotion strategy or campaign. */
export function NameTranslationsEditor({ kind, entityId }: Props) {
  const { t } = useTranslation('admin')
  const queryClient = useQueryClient()
  const [saved, setSaved] = useState(false)
  const [saveError, setSaveError] = useState<string | null>(null)

  const queryKey = adminTranslationQueryKeys.name(kind, entityId)
  const query = useQuery({
    queryKey,
    queryFn: ({ signal }) => getNameTranslations(kind, entityId, signal),
    retry: false,
  })

  const resolver = useMemo(
    () => zodResolver(createNameTranslationsSchema(t, NAME_MAX[kind])),
    [t, kind],
  )
  const form = useForm<FormInput, unknown, NameTranslationsFormValues>({
    resolver,
    defaultValues: { viName: '', enName: '' },
  })

  useEffect(() => {
    if (query.data) {
      form.reset(toFormValues(query.data))
    }
  }, [query.data, form])

  const mutation = useMutation({
    mutationFn: (values: NameTranslationsFormValues) =>
      putNameTranslations(kind, entityId, toPayload(values)),
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
  const prefix = `${kind}-translation`
  const nameLabel = t('translations.fieldName')

  return (
    <TranslationsEditorFrame
      title={kind === 'strategy' ? t('translations.strategyTitle') : t('translations.campaignTitle')}
      testId={`${kind}-translations-editor`}
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
          id={`${prefix}-vi-name`}
          label={`${nameLabel} (${t('translations.viSuffix')})`}
          error={errors.viName?.message}
        >
          <Input
            id={`${prefix}-vi-name`}
            disabled={busy}
            aria-invalid={Boolean(errors.viName)}
            {...form.register('viName')}
          />
        </TranslationField>
      </TranslationLocaleGroup>
      <TranslationLocaleGroup legend={t('translations.localeEn')}>
        <TranslationField
          id={`${prefix}-en-name`}
          label={`${nameLabel} (${t('translations.enSuffix')})`}
          error={errors.enName?.message}
        >
          <Input
            id={`${prefix}-en-name`}
            disabled={busy}
            aria-invalid={Boolean(errors.enName)}
            {...form.register('enName')}
          />
        </TranslationField>
      </TranslationLocaleGroup>
    </TranslationsEditorFrame>
  )
}
