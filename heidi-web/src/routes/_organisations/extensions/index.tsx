// SPDX-FileCopyrightText: 2025 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0

import {
  IconFileImport,
  IconPlus,
  IconPuzzle,
  IconTrash,
} from "@tabler/icons-react";
import { useSuspenseQuery } from "@tanstack/react-query";
import { createFileRoute, getRouteApi } from "@tanstack/react-router";
import { type ReactNode, useState } from "react";
import { FormattedMessage, useIntl } from "react-intl";
import { toast } from "sonner";
import { PageHeader } from "@/components/common/page-header";
import {
  AlertDialog,
  AlertDialogAction,
  AlertDialogCancel,
  AlertDialogContent,
  AlertDialogDescription,
  AlertDialogFooter,
  AlertDialogHeader,
  AlertDialogTitle,
  AlertDialogTrigger,
} from "@/components/ui/alert-dialog";
import { Button } from "@/components/ui/button";
import { Card } from "@/components/ui/card";
import {
  Dialog,
  DialogClose,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
  DialogTrigger,
} from "@/components/ui/dialog";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import type { Template } from "@/lib/api/templates/api";
import {
  useAddLibraryMutation,
  useDeleteLibraryMutation,
  useImportI14yTemplateMutation,
} from "@/lib/api/templates/mutations";
import { templateLibraryListOptions } from "@/lib/api/templates/query-options";

export const Route = createFileRoute("/_organisations/extensions/")({
  component: RouteComponent,
});

const routeApi = getRouteApi("/_organisations/extensions");
function RouteComponent() {
  const { crumb } = routeApi.useLoaderData();
  const { data: extensions } = useSuspenseQuery(templateLibraryListOptions());
  const { mutate: deleteLibrary } = useDeleteLibraryMutation();
  const { $t } = useIntl();
  return (
    <>
      <PageHeader heading={crumb}>
        <div className="flex flex-col gap-2 @lg:flex-row">
          <AddI14ySchemaDialog>
            <Button variant="outline" className="w-full @lg:w-auto">
              <IconFileImport className="size-4" />
              <FormattedMessage
                id="pages.extensions.i14y.addAction"
                defaultMessage="Import I14Y schema"
              />
            </Button>
          </AddI14ySchemaDialog>
          <AddExtensionDialog>
            <Button className="w-full @lg:w-auto">
              <IconPlus className="size-4" />
              <FormattedMessage
                id="common.add.withValue"
                defaultMessage="Add {value}"
                values={{
                  value: $t({
                    id: "common.extension",
                    defaultMessage: "Extension",
                  }),
                }}
              />
            </Button>
          </AddExtensionDialog>
        </div>
      </PageHeader>
      <div className="mt-3 flex flex-col gap-2">
        {extensions.length === 0 && (
          <Card className="flex justify-center border-dashed border-muted-foreground/50 py-5 text-muted-foreground">
            <FormattedMessage
              id="pages.extensions.none"
              defaultMessage="No Extensions"
            />
          </Card>
        )}
        {extensions.map((extension) => (
          <Card
            key={extension.id}
            className="grid gap-2 sm:flex sm:items-center sm:justify-between"
          >
            <div className="flex items-center gap-2">
              <IconPuzzle />
              <h2 className="truncate text-xl font-semibold">
                {extension.displayName}
              </h2>
            </div>
            <AlertDialog>
              <AlertDialogTrigger asChild>
                <Button variant="destructive">
                  <IconTrash />{" "}
                  <FormattedMessage
                    id="common.remove"
                    defaultMessage="Remove"
                  />
                </Button>
              </AlertDialogTrigger>
              <AlertDialogContent>
                <AlertDialogHeader>
                  <AlertDialogTitle>
                    <FormattedMessage
                      id="pages.extensions.delete.title"
                      defaultMessage="Are you absolutely sure?"
                    />
                  </AlertDialogTitle>
                  <AlertDialogDescription>
                    <FormattedMessage
                      id="pages.extensions.delete.description"
                      defaultMessage="This action cannot be undone. This will permanently delete the extension {extension}"
                      values={{
                        extension: (
                          <span className="inline-flex items-center gap-1 rounded-md border border-neutral-300 bg-neutral-50 p-1 px-1.5 text-xs leading-none shadow-xs">
                            {extension.displayName}
                          </span>
                        ),
                      }}
                    />
                  </AlertDialogDescription>
                </AlertDialogHeader>
                <AlertDialogFooter>
                  <AlertDialogCancel>
                    <FormattedMessage
                      id="common.cancel"
                      defaultMessage="Cancel"
                    />
                  </AlertDialogCancel>
                  <AlertDialogAction
                    variant="destructive"
                    onClick={() => deleteLibrary(extension.id)}
                  >
                    <IconTrash />
                    <FormattedMessage
                      id="common.remove"
                      defaultMessage="Remove"
                    />
                  </AlertDialogAction>
                </AlertDialogFooter>
              </AlertDialogContent>
            </AlertDialog>
          </Card>
        ))}
      </div>
    </>
  );
}

function AddI14ySchemaDialog({ children }: { children: ReactNode }) {
  const { mutateAsync: importTemplate } = useImportI14yTemplateMutation();
  const [isOpen, setIsOpen] = useState(false);
  const [datasetIdentifier, setDatasetIdentifier] = useState("");
  const [result, setResult] = useState<{
    template: Template;
    warnings: string[];
  }>();
  const { $t } = useIntl();

  async function handleImport() {
    if (!datasetIdentifier.trim()) {
      toast.error(
        $t({
          id: "pages.extensions.i14y.datasetId.required",
          defaultMessage: "Enter an I14Y dataset UUID or URN.",
        }),
      );
      return;
    }
    try {
      setResult(await importTemplate(datasetIdentifier.trim()));
    } catch {
      // The mutation displays the API error.
    }
  }

  return (
    <Dialog
      open={isOpen}
      onOpenChange={(open) => {
        setIsOpen(open);
        if (!open) {
          setDatasetIdentifier("");
          setResult(undefined);
        }
      }}
    >
      <DialogTrigger asChild>{children}</DialogTrigger>
      <DialogContent className="max-h-[90vh] overflow-y-auto">
        <DialogHeader>
          <DialogTitle>
            <FormattedMessage
              id="pages.extensions.i14y.title"
              defaultMessage="Import I14Y schema"
            />
          </DialogTitle>
          <DialogDescription>
            <FormattedMessage
              id="pages.extensions.i14y.description"
              defaultMessage="Enter the UUID or identifier URN of a published I14Y dataset. Its structure will be saved as a Heidi template."
            />
          </DialogDescription>
        </DialogHeader>
        <div className="flex flex-col gap-2">
          <Label htmlFor="i14y-dataset-id">
            <FormattedMessage
              id="pages.extensions.i14y.datasetId"
              defaultMessage="Dataset UUID or URN"
            />
          </Label>
          <div className="flex gap-2">
            <Input
              id="i14y-dataset-id"
              className="flex-1"
              value={datasetIdentifier}
              onChange={(event) => setDatasetIdentifier(event.target.value)}
              placeholder="UUID or urn:vct:..."
            />
            <Button variant="outline" onClick={handleImport}>
              <IconFileImport />
              <FormattedMessage
                id="common.import"
                defaultMessage="Import"
              />
            </Button>
          </div>
        </div>
        {result && (
          <div className="grid gap-2 rounded-xl border border-glacier-180 bg-glacier-200 p-3">
            <strong>{result.template.displayName}</strong>
            <span className="text-sm text-muted-foreground">
              {result.template.attributes.length} attributes
            </span>
            {result.warnings.length > 0 && (
              <ul className="list-disc pl-5 text-sm text-amber-700">
                {result.warnings.map((warning) => (
                  <li key={warning}>{warning}</li>
                ))}
              </ul>
            )}
          </div>
        )}
        <DialogFooter>
          <DialogClose asChild>
            <Button variant="outline">
              <FormattedMessage id="common.close" defaultMessage="Close" />
            </Button>
          </DialogClose>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}

interface Extension {
  key: string;
  displayName: string;
  templateUrls: string[];
}

function AddExtensionDialog({ children }: { children: ReactNode }) {
  const { mutate: addLibrary } = useAddLibraryMutation();
  const [isOpen, setIsOpen] = useState(false);
  const [url, setUrl] = useState("");
  const [extension, setExtension] = useState<Extension>();
  const { $t } = useIntl();
  async function handleSearch() {
    const res = await fetch(url);
    if (!res.ok) {
      toast.error(
        $t({
          id: "pages.extensions.toast.fetch.error",
          defaultMessage: "Failed to fetch extension.",
        }),
        {
          description: res.statusText,
        },
      );
      return;
    }
    const data = (await res.json()) as Extension;
    setExtension(data);
  }
  return (
    <Dialog open={isOpen} onOpenChange={setIsOpen}>
      <DialogTrigger asChild>{children}</DialogTrigger>
      <DialogContent>
        <DialogHeader>
          <DialogTitle>
            <FormattedMessage
              id="common.add.withValue"
              defaultMessage="Add {value}"
              values={{
                value: $t({
                  id: "common.extension",
                  defaultMessage: "Extension",
                }),
              }}
            />
          </DialogTitle>
          <DialogDescription className="sr-only">
            <FormattedMessage
              id="pages.extensions.addDialog.description"
              defaultMessage="Enter your extension URL in the input field below."
            />
          </DialogDescription>
        </DialogHeader>
        <div className="flex flex-col gap-2">
          <Label htmlFor="extension-url">
            <FormattedMessage
              id="pages.extensions.url"
              defaultMessage="Extension URL"
            />
          </Label>
          <div className="flex gap-2">
            <Input
              id="extension-url"
              className="flex-1"
              onChange={(e) => setUrl(e.target.value)}
              value={url}
            />
            <Button variant="outline" onClick={handleSearch}>
              <FormattedMessage id="common.search" defaultMessage="Search" />
            </Button>
          </div>
        </div>
        {extension && (
          <>
            <div className="flex flex-col gap-2">
              <Label>
                <FormattedMessage
                  id="pages.extensions.found"
                  defaultMessage="Extension Found"
                />
              </Label>
              <div className="flex gap-2 rounded-xl border border-glacier-180 bg-glacier-200 p-3">
                <IconPuzzle />
                <div className="flex flex-col gap-2">
                  <h3 className="text-xl leading-none font-semibold">
                    {extension.displayName}
                  </h3>
                  <p className="leading-none text-muted-foreground">
                    <FormattedMessage
                      id="pages.extensions.templatesCount"
                      defaultMessage="{count, plural, one {# Template} other {# Templates}}"
                      values={{ count: extension.templateUrls.length }}
                    />
                  </p>
                </div>
              </div>
            </div>
            <DialogFooter>
              <DialogClose asChild>
                <Button variant="outline">
                  <FormattedMessage
                    id="common.cancel"
                    defaultMessage="Cancel"
                  />
                </Button>
              </DialogClose>
              <Button
                onClick={() => {
                  addLibrary(url, {
                    onSuccess: () => {
                      setIsOpen(false);
                    },
                  });
                }}
              >
                <FormattedMessage
                  id="pages.extensions.addAction"
                  defaultMessage="Add Extension"
                />
              </Button>
            </DialogFooter>
          </>
        )}
      </DialogContent>
    </Dialog>
  );
}
