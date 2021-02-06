
# AddBatchSpecification
grpcurl -d '{"name": "zambezia-all", "description": "zambezia stamps", "url": "https://www.delcampe.net/en_US/collectibles/search?term=&categories%5B%5D=6315&search_mode=all&order=sale_start_datetime&display_state=sold_items", "provider": "delcampe", "intervalSeconds": 60 }' -plaintext 127.0.0.1:8101 proto.PriceScraperService.AddBatchSpecification

# PauseBatchSpecification
grpcurl -d '{"batchSpecificationId": "......"}' -plaintext 127.0.0.1:8101 proto.PriceScraperService.PauseBatchSpecification
