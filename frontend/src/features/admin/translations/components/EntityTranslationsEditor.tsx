import { zodResolver } from '@hookform/resolvers/zod'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useEffect, useMemo, useState } from 'react'
import { useForm } from 'react-hook-form'
import { useTranslation } from 'react-i18next'

import { Input } from '@/components/ui/input'
import { Textarea } from '@/components/ui/textarea'
import { ApiClientError } from '@/lib/api/api-error'

import { getEntityTranslations, putEntityTranslations } from '../api/admin-translations-api'
import { adminTranslationQueryKeys } from '../hooks/admin-translation-query-keys'
import { invalidateAfterTranslationSave } from '../hooks/invalidate-after-translation-save'
import {
  createEntityTranslationsSchema,
  type EntityTranslationsFormValues,
} from '../schemas'
import type {
  EntityTranslationKind,
  EntityTranslationsPayload,
  EntityTranslationsResponse,
} from '../types'
import {
  TranslationField,
  TranslationLocaleGroup,
  TranslationsEditorFrame,
} from './TranslationsEditorFrame'

type FormInput = {
  viName: string
  viDescription: string
  enName: string
  enDescription: string
}

const LIMITS: Record<EntityTranslationKind, { nameMax: number; descriptionMax: number }> = {
  product: { nameMax: 200, descriptionMax: 10_000 },
  category: { nameMax: 120, descriptionMax: 4_000 },
}

function toFormValues(data: EntityTranslationsResponse): FormInput {
  return {
    viName: data.translations.vi?.name ?? '',
    viDescription: data.translations.vi?.description ?? '',
    enName: data.translations.en?.name ?? '',
    enDescription: data.translations.en?.description ?? '',
  }
}

function toPayload(values: EntityTranslationsFormValues): EntityTranslationsPayload {
  const description = (value: string) => (value.trim() ? value.trim() : null)
  const payload: EntityTranslationsPayload = {
    translations: {
      vi: { name: values.viName.trim(), description: description(values.viDescription) },
    },
  }
  if (values.enName.trim()) {
    payload.translations.en = {
      name: values.enName.trim(),
      description: description(values.enDescription),
    }
  }
  return payload
}

type Props = {
  kind: EntityTranslationKind
  entityId: string
}

/** VI + EN name/description editor for an existing product or category. */
export function EntityTranslationsEditor({ kind, entityId }: Props) {
  const { t } = useTranslation('admin')
  const queryClient = useQueryClient()
  const [saved, setSaved] = useState(false)
  const [saveError, setSaveError] = useState<string | null>(null)

  const queryKey = adminTranslationQueryKeys.entity(kind, entityId)
  const query = useQuery({
    queryKey,
    queryFn: ({ signal }) => getEntityTranslations(kind, entityId, signal),
    retry: false,
  })

  const resolver = useMemo(
    () => zodResolver(createEntityTranslationsSchema(t, LIMITS[kind])),
    [t, kind],
  )
  const form = useForm<FormInput, unknown, EntityTranslationsFormValues>({
    resolver,
    defaultValues: { viName: '', viDescription: '', enName: '', enDescription: '' },
  })

  useEffect(() => {
    if (query.data) {
      form.reset(toFormValues(query.data))
    }
  }, [query.data, form])

  const mutation = useMutation({
    mutationFn: (values: EntityTranslationsFormValues) =>
      putEntityTranslations(kind, entityId, toPayload(values)),
    onSuccess: async (data) => {
      setSaveError(null)
      setSaved(true)
      queryClient.setQueryData(queryKey, data)
      form.reset(toFormValues(data))
      await invalidateAfterTranslationSave(queryClient, kind)
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
  const descriptionLabel = t('translations.fieldDescription')

  return (
    <TranslationsEditorFrame
      title={kind === 'product' ? t('translations.productTitle') : t('translations.categoryTitle')}
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
        <TranslationField
          id={`${prefix}-vi-description`}
          label={`${descriptionLabel} (${t('translations.viSuffix')})`}
          error={errors.viDescription?.message}
        >
          <Textarea
            id={`${prefix}-vi-description`}
            rows={3}
            disabled={busy}
            aria-invalid={Boolean(errors.viDescription)}
            {...form.register('viDescription')}
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
        <TranslationField
          id={`${prefix}-en-description`}
          label={`${descriptionLabel} (${t('translations.enSuffix')})`}
          error={errors.enDescription?.message}
        >
          <Textarea
            id={`${prefix}-en-description`}
            rows={3}
            disabled={busy}
            aria-invalid={Boolean(errors.enDescription)}
            {...form.register('enDescription')}
          />
        </TranslationField>
      </TranslationLocaleGroup>
    </TranslationsEditorFrame>
  )
}
