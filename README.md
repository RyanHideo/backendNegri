# Backend do CCM

O servidor opera com um único catálogo lógico de CCM. O arquivo
`tags-ccm1.yml` e os endpoints `/api/modbus/ccm1/**` permanecem disponíveis por
compatibilidade com a instalação atual. Os caminhos equivalentes sob
`/api/modbus/ccm/**` também estão disponíveis.

## Equipamentos auxiliares

`GET /api/ccm/auxiliary` reúne a potência percentual do britador primário, o
semáforo e o contador de caminhões. Enquanto as tags reais não forem conhecidas,
o endpoint retorna `status: "unavailable"` e valores nulos.

Nenhum endereço Modbus foi reservado. Para habilitar a potência do britador
quando a tag for criada no catálogo, configure:

```properties
auxiliary.primary-crusher.enabled=true
auxiliary.primary-crusher.power-percent-tag=NOME_REAL_DA_TAG
```

O semáforo e o contador continuam controlados por `truckflow.properties` e
permanecem desativados enquanto seus nomes de tags estiverem vazios.
