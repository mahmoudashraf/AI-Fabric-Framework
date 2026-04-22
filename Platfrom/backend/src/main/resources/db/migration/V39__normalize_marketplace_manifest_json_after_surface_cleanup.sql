update platform_marketplace_plugin_versions
set manifest_json = replace(
    replace(
        replace(
            replace(
                replace(manifest_json, ',' || chr(10) || '  }', chr(10) || '  }'),
                ',' || chr(10) || '    }', chr(10) || '    }'
            ),
            ',' || chr(10) || '      }', chr(10) || '      }'
        ),
        ',' || chr(10) || '        }', chr(10) || '        }'
    ),
    ',' || chr(10) || '          }', chr(10) || '          }'
)
where plugin_id in (
    'mkp-template-commerce-shell',
    'mkp-template-support-desk-shell',
    'mkp-action-shopify-admin',
    'mkp-action-notifications',
    'mkp-data-commerce-catalog',
    'mkp-data-help-center',
    'mkp-data-policy-folder'
);
